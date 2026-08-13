# 여행 계획 앱 설계

## 개요

Kotlin + Spring Boot로 만드는 개인용 로컬 여행 계획 앱. 구글맵 공유 링크로 장소(소스)를 등록하고, 지도 기반 화면에서 일자별 타임테이블에 드래그앤드롭으로 배치한다. 단일 사용자, 단일 여행만 관리하며 데이터는 로컬 H2 파일에 저장한다.

## 목표

- 구글맵에서 공유한 장소를 빠르게 소스로 등록 (이름/좌표 자동 파싱)
- 장소별 예상 소요 시간, 종류(음식점/관광지), 예약 필요 여부와 마감일 관리
- 지도와 연동된 화면에서 일자별 동선을 시각적으로 구성
- 비밀번호로 간단히 보호된 로컬 개인용 앱

## 기술 스택

- Kotlin + Spring Boot (Web MVC, Thymeleaf, Spring Data JPA)
- H2 파일 모드 DB (`jdbc:h2:file:./data/juice-plan`), `ddl-auto=update`
- Thymeleaf 서버 렌더링 + Vanilla JS
- Google Maps JavaScript API (지도/마커), 드래그앤드롭은 HTML5 Drag and Drop API 또는 경량 라이브러리(SortableJS 등)
- 별도 인증 프레임워크 없이 세션 기반 자체 구현 (개인용 로컬 앱)
- **사전 준비물**: Google Maps JavaScript API 키 (환경변수/`application.yml`로 주입)

## 아키텍처

- 서버 렌더링(Thymeleaf) 위주 + 필요한 상호작용만 JSON API로 처리하는 하이브리드 구조.
- 페이지2(`/plan`)는 로드 시 전체 소스 데이터를 JSON으로 페이지에 임베드하고, 지도 상호작용(뷰포트 필터링, 클릭 강조)은 전부 클라이언트에서 처리해 서버 왕복을 최소화한다. 실제 데이터 변경(배정/해제/순서변경)만 경량 API로 자동저장한다.
- 페이지 구성:
  - `/` — 비밀번호 게이트 (최초 설정 또는 로그인)
  - `/sources` — 소스 관리
  - `/plan` — 일자별 동선
  - `/sources`, `/plan`은 공통 레이아웃 + 하단 풋터 탭으로 전환

## 인증 & 네비게이션

- `AppSettings` 단일 행에 비밀번호 해시(BCrypt)를 저장. 소스코드에는 비밀번호 값이 존재하지 않는다.
- `/` 접근 시:
  - 저장된 해시 없음 → 최초 비밀번호 설정 폼(입력 + 확인) 표시. 저장 시 해시되어 `AppSettings`에 저장되고 세션 인증 후 `/sources`로 리다이렉트.
  - 해시 있음 → 비밀번호 입력 폼. 일치하면 세션 인증 후 `/sources`로 리다이렉트. 불일치 시 같은 화면에 에러 표시.
- 인증된 세션 상태에서 `/`에 다시 접근하면 자동으로 `/sources`로 리다이렉트.
- 인터셉터가 `/sources`, `/plan` 및 관련 API에 대해 세션의 `authenticated` 플래그를 검사하고, 없으면 `/`로 리다이렉트.
- 인증은 **브라우저 세션 동안만 유지**된다 (기본 `HttpSession`, 별도 remember-me 쿠키 없음). 브라우저를 닫거나 서버가 재시작되면 재인증이 필요하다.
- `/sources`, `/plan`은 하단 고정 풋터의 탭 2개(소스 관리 / 일자별 동선)로 전환한다. 상단 네비게이션은 없다.

## 데이터 모델

### AppSettings (단일 행)
- `id: Long`
- `passwordHash: String`

### Trip (단일 행)
- `id: Long`
- `startDate: LocalDate`
- `endDate: LocalDate`

### Source
- `id: Long`
- `googleMapsUrl: String` — 원본 공유 링크(참고용)
- `name: String`
- `latitude: Double`, `longitude: Double`
- `placeType: enum { RESTAURANT, ATTRACTION }`
- `durationMinutes: Int`
- `reservationRequired: Boolean`
- `reservationDeadline: LocalDate?` — `reservationRequired=true`일 때만 값 존재
- `scheduledDate: LocalDate?` — null이면 미배정(가용 목록에 존재)
- `sortOrder: Int` — 같은 날짜 내 순서. 미배정 상태면 의미 없음

단일 여행만 존재하므로 `Source`에 `tripId` FK를 두지 않는다. 타임테이블 배정/해제/순서변경은 `scheduledDate` + `sortOrder` 두 필드만으로 표현한다.

## 페이지1 — 소스 관리 (`/sources`)

**여행 기간 설정**: 페이지 상단 위젯. Trip이 없으면 시작일/종료일 입력 폼이 바로 보이고, 있으면 요약 표시 후 클릭 시 편집.

**소스 추가 폼**
1. 구글맵 공유 링크 입력 + "가져오기" 버튼 → `POST /api/sources/parse-link {url}`
   - 서버가 단축링크 리다이렉트를 따라가 최종 URL을 얻고, `/place/<이름>/`와 `@<lat>,<lng>,<zoom>z` 패턴을 정규식으로 파싱.
   - 성공: 이름/위도/경도 자동 채움(수정 가능) + 미리보기 지도에 핀 표시.
   - 실패: 안내 메시지 + 미리보기 지도의 핀을 직접 드래그해 좌표 지정하는 수동 입력 모드로 전환. 이름도 직접 입력.
2. 장소 종류: 음식점 / 관광지 (라디오 버튼)
3. 예상 소요 시간: 시간 + 분 입력 → 합산해 `durationMinutes`로 저장
4. 예약 필요 여부 체크박스 → 체크 시 예약 마감일 날짜 입력 노출 (체크했는데 날짜 미입력이면 저장 시 검증 오류)
5. 저장 → `POST /sources`

**등록된 소스 목록**: 이름/종류/소요시간/예약정보/배정 상태를 표시. 수정(`PUT /sources/{id}`)과 삭제(`DELETE /sources/{id}`, 확인 후 삭제) 가능. 삭제 시 스케줄에서도 함께 제거된다.

## 페이지2 — 일자별 동선 (`/plan`)

**레이아웃**
- 상단: 날짜 탭 (여행 시작일~종료일 범위로 자동 생성)
- 좌측: 가용 소스 목록 — 미배정(`scheduledDate == null`) 소스 중 현재 지도 뷰포트 안에 있는 것만 표시
- 중앙: 구글 지도 — 전체 소스의 마커를 항상 표시(종류별 색 구분)
- 하단/우측: 선택된 날짜의 타임테이블 — 배정된 소스를 `sortOrder` 순서로 나열

**데이터 로딩**: 서버가 렌더링 시 전체 Source + Trip 기간을 JSON으로 페이지에 임베드. 이후 상호작용은 클라이언트 메모리 배열 기준으로 동작하고, 실제 변경만 서버에 저장.

**상호작용**
- 목록 클릭 → 지도 `panTo` + 마커 바운스로 위치 표시
- 지도 `bounds_changed`(디바운스) → 가용 목록을 뷰포트 내 미배정 소스만 남도록 클라이언트에서 재계산 (서버 왕복 없음)
- 가용 목록 카드를 드래그(또는 클릭)해 선택된 날짜 타임테이블에 추가
- 타임테이블 내 드래그로 순서 변경
- 타임테이블 항목의 "X" 버튼 또는 풀 목록으로 드래그해 제거 → 다시 미배정 상태로 복귀

**API**
- `POST /api/schedule/day/{date}` — body: 그 날짜의 전체 소스 id 순서 배열. 배열 순서대로 `scheduledDate`/`sortOrder`를 일괄 갱신. **이 배열은 해당 날짜에 대해 authoritative하게 취급되어, 이전에 그 날짜에 배정돼 있었지만 배열에 없는 소스는 자동으로 미배정 처리된다.** 추가/순서변경/다른 날짜로 이동을 이 엔드포인트 하나로 처리한다.
- `DELETE /api/schedule/{sourceId}` — `scheduledDate=null`, `sortOrder=0`으로 초기화.
- 저장 실패 시 토스트 알림 + 드래그 이전 상태로 UI 롤백. 성공 응답 전에는 낙관적 확정을 하지 않는다.

## 에러 처리

- 링크 파싱 실패: 서버는 예외가 아니라 `{success:false}` 응답으로 표현, 클라이언트가 수동 입력 폴백 UI로 전환.
- 인증 실패: 같은 화면에 에러 메시지, 별도 잠금/속도제한 없음.
- 여행 기간 미설정 상태에서 `/plan` 접근: "먼저 여행 기간을 설정해주세요" 안내 + `/sources` 링크.
- 입력 검증: 예약 필요 시 마감일 누락, 여행 시작일 > 종료일 등은 서버에서 검증 후 필드별 에러 메시지 반환.
- 스케줄 API 실패: 토스트 알림 + UI 롤백.
- `@ControllerAdvice`로 API는 JSON 에러 응답, 폼 요청은 에러 페이지/플래시 메시지로 일관 처리.

## 테스트 전략

- **단위 테스트** (JUnit5 + MockK): 구글맵 링크 파서(정상/리다이렉트 실패/좌표 없음), 시간·분 → `durationMinutes` 변환, 예약 필요 시 마감일 검증, 일자별 순서 저장 서비스(배열대로 재부여 + 배열에서 빠진 기존 항목 자동 미배정).
- **통합 테스트** (`@SpringBootTest` + MockMvc, H2 인메모리 test profile): 인증 플로우(최초 설정/로그인 성공·실패/미인증 접근 차단), 소스 CRUD API, 스케줄 배정/해제 API.
- **수동 검증**: 지도 클릭 강조, 뷰포트 필터링, 드래그앤드롭 등 JS 상호작용은 자동화 테스트 범위 밖이며 실제 브라우저로 직접 확인한다.

## 범위 밖 (Future Work)

- 다중 여행 관리
- 시각화된 시간대 배치(간트/캘린더 형태)
- 구글 Places API 기반 검색 자동완성
- 비밀번호 변경/재설정 기능
- 원격 배포, 다중 사용자 지원
