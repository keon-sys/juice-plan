# 인증 제거 + 공통 지도 셸 구조 설계

날짜: 2026-08-15

이 문서는 `2026-08-15-mobile-redesign-timetable-design.md`의 후속이다. 그 스펙이 만든 3화면 구조를 단일 셸로 재편한다. 타임테이블 규칙(04:00~28:00, 30분 슬롯, 겹침 가로분할)과 데이터 모델(`startMinutes`)은 그대로 유지한다.

## 배경

직전 작업으로 세 화면에 디자인과 타임테이블 배정이 들어갔지만 세 가지 문제가 남았다.

1. **지도가 화면마다 따로 있다.** 탭을 옮길 때마다 페이지가 리로드되고 구글맵이 통째로 다시 만들어진다. 깜빡이고, 구글맵 API 호출도 매번 새로 나간다. 장소를 등록하는 화면에는 지도가 아예 없어서 위치를 눈으로 확인할 수 없다.
2. **참고사항 저장 버튼이 전체 폭이라** 그 날의 메모가 아니라 전체 계획을 저장하는 버튼처럼 보인다.
3. **계획 보기가 카드 목록이라** 편집 화면의 타임테이블과 그림이 달라 시간 감각이 이어지지 않는다.

또한 비밀번호 게이트는 개인용 로컬 앱에 비해 과하다.

## 목표

- 지도를 세 뷰가 공유하고, 지도 아래 영역만 전환한다. 지도는 한 번만 생성한다.
- 지도를 접어 뷰가 화면을 다 쓰게 한다.
- 계획 보기도 타임테이블로 그린다.
- 인증을 제거한다.
- 탭 이름을 장소 추가 / 동선 변경 / 계획 보기로 바꾼다.

## 비목표

- 지도를 탭해서 좌표를 찍는 장소 등록 (링크 붙여넣기와 직접 입력으로 충분하다)
- 브라우저 History API 기반 라우팅 (해시로 충분하다)
- 오프라인 동작, 서비스 워커
- 인증을 나중에 되살리기 위한 설정 스위치

---

## 1. 인증 제거

### 삭제 대상

| 파일 | 비고 |
|---|---|
| `auth/AuthController.kt` | |
| `auth/AuthService.kt` | |
| `auth/AuthInterceptor.kt` | `SESSION_AUTHENTICATED_KEY` 상수 포함 |
| `auth/AppSettings.kt` | |
| `auth/AppSettingsRepository.kt` | |
| `config/WebConfig.kt` | 인터셉터가 없어져 파일 자체가 불필요 |
| `templates/auth/login.html` | |
| `templates/auth/setup.html` | |
| `test/auth/AuthServiceTest.kt` | |
| `test/auth/AuthInterceptorTest.kt` | |
| `test/auth/AuthFlowIntegrationTest.kt` | |

`build.gradle.kts`의 `spring-boot-starter-security`(BCrypt 용도)도 다른 곳에서 쓰지 않으면 제거한다.

`APP_SETTINGS` 테이블은 마이그레이션에서 `DROP TABLE IF EXISTS APP_SETTINGS`로 정리한다.

### 프론트 정리

`plan.js`, `sources.js`의 `isUnauthorized` 함수와 "세션이 만료되었습니다" 처리, 401 분기를 전부 제거한다.

### 보안 영향

인증이 사라지면 이 앱에 네트워크로 닿을 수 있는 누구나 여행 일정을 읽고 고칠 수 있다. **로컬에서만 실행하는 것을 전제**로 한 결정이다. 외부에 노출한다면 인증을 되살리거나 리버스 프록시 단에서 막아야 한다.

---

## 2. 라우팅

서버 라우트는 셸 하나뿐이다. 뷰 전환은 URL 해시로 한다.

| 해시 | 뷰 | 탭 이름 |
|---|---|---|
| `#day` (기본) | 계획 보기 | 계획 보기 |
| `#plan` | 동선 편집 | 동선 변경 |
| `#add` | 장소 등록/관리 | 장소 추가 |

- `GET /` → 셸. 해시가 없거나 알 수 없는 값이면 `#day`로 친다.
- `GET /sources`, `GET /plan`, `GET /day` → `/`로 302 리다이렉트. 북마크를 살린다.
- `hashchange` 이벤트로 뷰를 갈아끼운다. 뒤로 가기가 자연스럽게 동작한다.

여행 기간이 설정되지 않았으면 어떤 해시로 들어와도 **장소 추가 뷰**를 보여주고 기간 설정 카드를 띄운다. 날짜가 없으면 나머지 두 뷰가 그릴 게 없다.

---

## 3. 셸 구조

```
┌─────────────────────┐
│      지 도 (200px)    │  한 번만 생성, 재로드 없음
├══════ 핸들바 ═════════┤  탭: 접기/펴기, 드래그: 따라 이동
│                     │
│    뷰 영역 (전환)     │
│                     │
├─────────────────────┤
│ 장소추가 동선변경 계획보기 │
└─────────────────────┘
```

### 접기 동작

지도는 자리에 그대로 두고 **뷰 영역이 위로 올라가 덮는다** (`transform: translateY(-200px)`). 지도를 `display:none` 하거나 높이를 0으로 만들지 않는다. 구글맵은 크기가 0이 되면 타일 로딩이 깨지고 다시 펼 때 `resize` 이벤트를 쏴야 한다.

- 핸들바 **탭**: 접힘 ↔ 펼침 토글
- 핸들바 **드래그**: 손가락을 따라 `translateY`가 0~-200px 사이에서 움직인다
- 손을 떼면 이동량이 절반(100px)을 넘었는지로 접힘/펼침을 정한다
- 전환은 `transition: transform .25s ease`. 드래그 중에는 transition을 끈다.

`dragdrop.js`의 `makeDraggable`을 재사용한다. `onTap`으로 토글, `onMove`로 추적, `onDrop`으로 스냅한다.

### 뷰별 지도 표시

| 뷰 | 지도가 보여주는 것 |
|---|---|
| 장소 추가 | 전체 소스 핀. 카드를 탭하면 그 핀으로 이동. 시트에서 위경도가 채워지면 미리보기 핀 표시 |
| 동선 변경 | 전체 핀 + 선택된 날짜의 동선을 시간순 선으로 연결. 지도 영역으로 왼쪽 레일 필터링 |
| 계획 보기 | 선택된 날짜의 동선만 번호 마커 + 선. 그 날 동선에 맞춰 `fitBounds` |

뷰를 바꿀 때 마커를 전부 지우고 다시 그린다. 지도 인스턴스 자체는 유지된다.

---

## 4. 뷰 구성

### 4.1 장소 추가 `#add`

여행 기간 카드 → 필터 칩(전체/음식점/관광지/미배정) → 소스 카드 리스트 → FAB(+) → 하단 시트.

직전 스펙의 구성을 유지하되 두 가지가 바뀐다.

- 카드를 탭하면 지도가 그 위치로 이동한다.
- 저장·수정·삭제가 폼 전송이 아니라 JSON API 호출이다. 페이지를 리로드하지 않고 목록만 다시 그린다.

시트에서 "가져오기"로 위경도가 채워지면 지도에 **미리보기 핀**(기존 핀과 구분되는 색)을 띄우고 그 위치로 이동한다. 시트를 닫으면 미리보기 핀은 사라진다.

### 4.2 동선 변경 `#plan`

날짜 스트립 → 참고사항 → 좌 소스 레일 + 우 타임테이블.

타임테이블 드래그 배정은 직전 스펙 그대로다. 참고사항이 위로 올라오면서 타임테이블 높이가 줄어드는데, 참고사항이 접힌 상태에서는 한 줄(약 44px)만 차지하므로 손실이 작다.

### 4.3 계획 보기 `#day`

날짜 스트립 → 참고사항 → 읽기 전용 타임테이블(전체 폭).

- 같은 `TimeGrid`로 그린다. 소스 레일이 없어 타임테이블이 화면 폭을 다 쓴다.
- 드래그와 시각 수정 시트가 없다. 블록을 탭하면 지도가 그 장소로 이동한다.
- 블록에 순번을 붙여 지도의 번호 마커와 짝이 맞게 한다.
- 열릴 때 **그 날 첫 일정의 30분 전 위치로 자동 스크롤**한다. 04:00부터 보여주면 대부분 빈 공간이다. 일정이 없으면 09:00 위치로 둔다.
- 일정이 없는 날은 타임테이블 대신 "이 날은 아직 일정이 없습니다."를 띄운다.

### 4.4 참고사항 컴포넌트

두 뷰가 같은 컴포넌트를 쓴다. 기본은 접힘이다.

```
📝 참고사항  숙소 체크인 15시 이후                  ▾     접힘
📝 참고사항                                       ▴     펼침
┌────────────────────────────────────┐
│ 숙소 체크인 15시 이후                   │
└────────────────────────────────────┘
                             [ 저장 ]        작게, 오른쪽 정렬
```

- 접힘 상태에서는 메모 첫 줄을 한 줄로 잘라 보여준다. 없으면 "참고사항 없음"을 흐리게.
- 헤더를 탭하면 펼쳐진다.
- 저장 버튼은 전체 폭이 아니라 **내용 폭, 오른쪽 정렬**이다. 그 날 메모만 저장한다는 게 드러나야 한다.
- 계획 보기에서는 textarea와 저장 버튼 없이 내용만 보여준다.

---

## 5. 백엔드 변경

### 5.1 신규: ShellController

```kotlin
@GetMapping("/")
fun shell(model: Model): String   // "shell/index"
```

모델에 `trip`(없으면 null), `sources`, `dayNotes`를 담는다. `dayNotes`는 여행 기간이 있을 때만 조회하고, 없으면 빈 맵을 넣는다.

옛 경로 리다이렉트:

```kotlin
@GetMapping("/sources", "/plan", "/day")
fun legacy(): RedirectView = RedirectView("/")
```

`PlanController`, `DayViewController`, `SourceController`의 `@GetMapping("/sources")`는 삭제한다.

### 5.2 소스 CRUD를 JSON으로

폼 전송 + 리다이렉트는 페이지를 리로드시켜 지도를 날린다. JSON API로 바꾼다.

| 지금 | 바뀐 뒤 |
|---|---|
| `POST /sources` (form → redirect) | `POST /api/sources` → 저장된 소스 JSON |
| `PUT /sources/{id}` (form → redirect) | `PUT /api/sources/{id}` → 갱신된 소스 JSON |
| `DELETE /sources/{id}` (→ redirect) | `DELETE /api/sources/{id}` → 200 |
| `POST /trip` (form → redirect) | `POST /api/trip` → 저장된 Trip JSON |

요청 본문은 기존 `SourceForm`과 같은 필드를 JSON으로 받는다.

```kotlin
data class SourceRequest(
    val googleMapsUrl: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val placeType: PlaceType,
    val durationHours: Int,
    val durationMinutesPart: Int,
    val reservationRequired: Boolean = false,
    val reservationDeadline: LocalDate?,
    val memo: String?
)

data class TripRequest(val startDate: LocalDate, val endDate: LocalDate)
```

응답으로 저장된 엔티티를 돌려주는 이유: 클라이언트가 생성된 `id`를 알아야 목록에 넣고 이후 수정·삭제·배정을 걸 수 있다.

`SourceService`, `TripService`는 그대로 둔다. 컨트롤러 계층만 바뀐다.

### 5.3 GlobalExceptionHandler 단순화

모든 쓰기가 `/api/`로 옮겨가 리다이렉트 분기가 죽는다. `IllegalArgumentException`을 항상 `400 {"error": "..."}`로 변환한다. `RedirectAttributes` 의존도 사라진다.

**`NoSuchElementException` 핸들러를 추가한다.** 지금은 핸들러가 없어 없는 id로 수정·삭제·배정하면 500이 난다. `404 {"error": "..."}`로 변환한다. `SourceService.get`, `ScheduleService.assign/remove`가 모두 이 예외를 던지므로 셋 다 한 번에 고쳐진다.

### 5.4 마이그레이션

`ScheduleTimeMigration`에 `APP_SETTINGS` 테이블 정리를 더한다. `SORT_ORDER` 처리와 독립적으로, 매 기동 시 실행해도 안전하다.

```sql
DROP TABLE IF EXISTS APP_SETTINGS
```

클래스 이름을 `SchemaMigration`으로 바꾼다. 하는 일이 시각 마이그레이션만이 아니게 됐다.

---

## 6. 파일 구조

**신규**
| 파일 | 책임 |
|---|---|
| `shell/ShellController.kt` | `/` 렌더 + 옛 경로 리다이렉트 |
| `templates/shell/index.html` | 셸 마크업 (지도 + 세 뷰 + 탭) |
| `static/js/shell.js` | 해시 라우팅, 뷰 전환, 지도 접기/드래그 |
| `static/js/mapview.js` | 지도 인스턴스 + 마커/폴리라인 관리 (뷰들이 공유) |
| `static/js/daynote.js` | 참고사항 접힘/펼침 컴포넌트 |
| `static/js/view-add.js` | 장소 추가 뷰 |
| `static/js/view-plan.js` | 동선 변경 뷰 (기존 plan.js에서 지도·날짜 부분을 뺀 것) |
| `static/js/view-day.js` | 계획 보기 뷰 |
| `static/js/datestrip.js` | 날짜 스트립 (두 뷰 공유) |

**수정**: `SourceController.kt`(JSON), `TripController.kt`(JSON), `GlobalExceptionHandler.kt`, `ScheduleTimeMigration.kt`→`SchemaMigration.kt`, `style.css`, `timegrid.js`(변경 없음, 재사용)

**삭제**: 1절 목록 + `templates/sources/`, `templates/plan/`, `templates/dayview/`, `static/js/plan.js`, `static/js/sources.js`, `static/js/dayview.js`, `plan/PlanController.kt`, `dayview/DayViewController.kt`

`plan.js`가 400줄 넘게 커졌으므로 지도·날짜 스트립·참고사항을 별도 파일로 떼어 세 뷰가 공유하게 한다.

---

## 7. 테스트

### 삭제
`AuthServiceTest`, `AuthInterceptorTest`, `AuthFlowIntegrationTest`, `PlanControllerIntegrationTest`, `DayViewControllerIntegrationTest`

### 신규: ShellControllerIntegrationTest
- `/`가 200이고 뷰 이름이 `shell/index`
- 여행과 소스가 있을 때 `SOURCES`, `TRIP`, `DAY_NOTES`가 HTML에 실려 나간다
- 여행이 없을 때도 200이고 `TRIP`이 null로 나간다
- `/sources`, `/plan`, `/day`가 각각 `/`로 302

### 재작성: SourceControllerIntegrationTest
쓰기 응답은 전부 **200**이다 (201을 쓰지 않는다 — 클라이언트가 Location 헤더를 쓰지 않고 본문의 id만 본다).

- `POST /api/sources`가 200과 함께 `id`가 담긴 소스 JSON을 돌려준다
- 예약 필요인데 마감일이 없으면 400과 `"예약이 필요한 경우 예약 마감일을 입력해야 합니다."`
- `PUT /api/sources/{id}`가 갱신된 JSON을 돌려준다
- `DELETE /api/sources/{id}` 후 목록에서 사라진다
- 없는 id로 `PUT`/`DELETE` 하면 **404**

### 재작성: TripControllerIntegrationTest
- `POST /api/trip`이 200과 저장된 기간 JSON을 돌려준다
- 두 번 호출하면 새로 만들지 않고 기존 것을 갱신한다 (`TripService.save`가 upsert다)
- 시작일이 종료일보다 늦으면 400과 `"시작일은 종료일보다 늦을 수 없습니다."`

### 정리
`DayNoteControllerIntegrationTest`, `ScheduleControllerIntegrationTest`에서 `MockHttpSession`과 `SESSION_AUTHENTICATED_KEY` 설정, 401 테스트를 제거한다.

### 마이그레이션
`SchemaMigrationTest`에 `APP_SETTINGS` 테이블이 지워지는지, 없는 상태에서 다시 돌려도 안전한지를 더한다.

### JS
`timegrid.js` 순수 함수는 node로 검증하는 방식을 유지한다. 새로 생기는 순수 계산도 같은 방식으로 검증한다.

- `firstScrollTarget(items) -> px` — 계획 보기가 열릴 때 스크롤할 위치. 첫 일정 30분 전, 일정이 없으면 09:00.

### 수동 확인 체크리스트

- [ ] 탭을 옮겨도 지도가 다시 로드되지 않는다 (깜빡임 없음, 지도 중심·줌이 유지됨)
- [ ] 핸들바를 탭하면 뷰가 지도를 덮고, 다시 탭하면 되돌아온다
- [ ] 핸들바를 끌면 손가락을 따라오고, 절반을 넘겨 놓으면 그쪽으로 붙는다
- [ ] 지도를 펼쳤을 때 타일이 깨지지 않는다
- [ ] 장소 추가에서 카드를 탭하면 지도가 그 위치로 간다
- [ ] 시트에서 링크를 가져오면 미리보기 핀이 뜨고, 닫으면 사라진다
- [ ] 장소를 추가·수정·삭제해도 페이지가 리로드되지 않고 목록만 갱신된다
- [ ] 동선 변경의 드래그 배정이 직전과 동일하게 동작한다
- [ ] 계획 보기가 타임테이블로 나오고 첫 일정 근처로 스크롤돼 열린다
- [ ] 계획 보기에서 블록을 탭하면 지도가 이동한다
- [ ] 참고사항이 접힌 한 줄로 나오고, 펼치면 저장 버튼이 작게 오른쪽에 붙는다
- [ ] 날짜를 바꾸면 참고사항·타임테이블·지도가 함께 바뀐다
- [ ] 뒤로 가기가 이전 탭으로 돌아간다
- [ ] `/sources`로 접속하면 `/`로 이동한다
- [ ] 여행 기간이 없으면 장소 추가 뷰가 기간 설정 카드와 함께 뜬다
- [ ] 다크모드에서 모든 뷰가 읽힌다
