# 모바일 리디자인 + 시각 기반 타임테이블 배정 설계

날짜: 2026-08-15

## 배경

juice-plan은 여행 소스(음식점/관광지)를 등록하고 날짜별 동선을 짜는 개인용 Spring Boot + Thymeleaf 앱이다. 두 가지 문제가 있다.

1. **디자인이 없다.** `style.css`가 9줄뿐이고 사실상 스타일 없는 HTML이다.
2. **폰에서 일자 배정이 불가능하다.** 배정은 HTML5 `dragstart`/`drop` 이벤트에만 연결돼 있는데, 모바일 브라우저는 이 이벤트를 발생시키지 않는다. 백엔드(`ScheduleService.assignDay`)와 API는 동작하지만 폰에서는 손댈 방법이 없다.

또한 현재 모델에는 **시각 개념이 없다.** 소스는 `scheduledDate`(날짜)와 `sortOrder`(그 날 안에서의 순서)만 가지며, "11시에 아사쿠사, 13시에 라멘" 같은 계획을 표현할 수 없다.

## 목표

- 폰 화면(가로 390px 기준)을 기준으로 전체 화면에 일관된 디자인 시스템을 입힌다.
- 날짜를 먼저 고른 뒤, 소스를 04:00~28:00 타임테이블의 원하는 시각에 드래그해서 놓을 수 있게 한다.
- 짜둔 하루를 지도와 함께 읽기 전용으로 보는 화면을 추가한다.

## 비목표

- JS 테스트 러너 도입 (별건)
- 여행 전체를 한 눈에 보는 화면
- 여러 여행 관리 (현재도 여행은 하나)
- 이동 시간 자동 계산 / 경로 최적화

---

## 1. 디자인 시스템

`src/main/resources/static/css/style.css`를 처음부터 다시 쓴다. 외부 CSS 프레임워크나 CDN 의존성은 쓰지 않는다.

### 토큰

| 토큰 | 값 | 용도 |
|---|---|---|
| `--bg` | `#F5F6F8` | 화면 바탕 |
| `--surface` | `#FFFFFF` | 카드·시트 |
| `--primary` | `#4F6BED` | 선택 상태, 주요 버튼 |
| `--food` | `#FF7A59` | 음식점 |
| `--attraction` | `#2BB0A0` | 관광지 |
| `--text` | `#1B1F27` | 본문 |
| `--muted` | `#6B7280` | 보조 텍스트 |
| `--border` | `#E5E7EB` | 구분선 |
| `--radius` | `16px` | 카드 |
| `--radius-sm` | `10px` | 배지, 작은 버튼 |

간격은 `4 / 8 / 12 / 16 / 24`의 배수만 쓴다.

### 규칙

- 폰트는 시스템 스택(`-apple-system, "Segoe UI", "Noto Sans KR", sans-serif`). 웹폰트를 받지 않는다.
- 다크모드는 `@media (prefers-color-scheme: dark)`에서 **토큰 값만 재정의**한다. 레이아웃 CSS는 건드리지 않는다.
- 색만으로 정보를 전달하지 않는다. 음식점/관광지는 색과 함께 아이콘(🍴/📍)을 항상 표시한다.
- 탭·버튼 등 터치 대상은 최소 44px.
- 하단 탭은 `padding-bottom: env(safe-area-inset-bottom)`으로 홈 인디케이터를 피한다.

### 컴포넌트

필요한 만큼만 만든다: 카드, 배지(장소 종류/예약), 칩(필터), 하단 시트, 날짜 스트립, 타임그리드, FAB, 하단 탭바.

---

## 2. 화면 구조

하단 탭이 2개에서 3개로 늘어난다.

### 2.1 소스 관리 `/sources`

- 상단: 여행 기간 카드 (미설정이면 설정 폼, 설정됐으면 기간 표시 + 수정)
- 필터 칩: 전체 / 음식점 / 관광지 / 미배정
- 소스 카드 리스트: 이름, 종류 배지, 소요시간, 배정 상태(`8/15 11:00` 또는 `미배정`), 예약 배지, 비고
- 우하단 FAB(+) → **하단 시트**로 추가 폼. 수정도 같은 시트를 재사용한다.
- 현재의 긴 인라인 폼과 `display:none` 토글은 제거한다.

### 2.2 일자별 동선 `/plan` (편집)

위에서 아래로:

1. **날짜 스트립** — 여행 기간의 날짜를 가로 스크롤. 각 칸에 요일 + 일자 + 그 날 배정 개수. 선택된 날짜 강조.
2. **지도** — 접기 가능. 전체 소스 핀 표시(종류별 색), 그 날 배정된 소스는 시간순으로 선 연결. 소스를 탭하면 지도가 해당 위치로 이동.
3. **좌우 분할** (화면 나머지를 채움)
   - 왼쪽 레일(약 34%): 미배정 소스 카드 세로 목록. 지도 영역 필터는 유지한다.
   - 오른쪽(약 66%): 04:00~28:00 타임테이블, 세로 스크롤.
4. **참고사항** — 그 날짜의 `DayNote` 입력.

### 2.3 계획 보기 `/day` (신규, 읽기 전용)

- 날짜 스트립 (편집 화면과 동일 컴포넌트)
- 지도: 그 날 동선만 **시간순 번호 마커 + 연결선**
- 아래: 시간순 일정 카드 리스트 (`11:00–12:30 아사쿠사 절`), 예약 정보, 비고
- 맨 아래: 그 날 참고사항
- 편집 조작은 없다.

`AuthInterceptor`가 모든 요청을 보호하므로 `/day`도 자동으로 로그인 필요 상태가 된다. 별도 설정은 없다.

---

## 3. 타임테이블 렌더링

- 범위 04:00 ~ 28:00 = 24시간. 30분 = 1슬롯, 슬롯 높이 **28px** → 총 48슬롯, 1344px 세로 스크롤.
- 시간 라벨은 정시마다. 24시 이후는 `25:00`, `26:00`처럼 표기한다(다음날 새벽).
- 블록 위치: `top = (startMinutes - 240) / 30 * 28px`
- 블록 높이: `durationMinutes / 30 * 28px`, 최소 1슬롯(28px)
- 블록 내용: 시각 범위, 이름, 종류 아이콘. 높이가 1슬롯이면 이름만 표시한다.
- 예약 필요 소스는 블록 왼쪽에 굵은 바 + 🔔
- **28:00을 넘기는 블록**은 막지 않는다. 28:00에서 잘라 그리고 블록에 `→ 28:00+`를 표시한다.

### 겹침 처리

겹침은 허용한다. 구글 캘린더 방식으로 가로 분할한다.

1. 그 날 블록을 `startMinutes` 오름차순 정렬
2. 시간이 겹치는 블록끼리 그룹으로 묶는다
3. 그룹 안에서 각 블록에 컬럼을 배정한다 (앞 블록과 안 겹치는 가장 왼쪽 컬럼)
4. `width = 100% / 그룹의 컬럼 수`, `left = 컬럼 인덱스 × width`

이 계산은 순수 함수 `layoutBlocks(blocks) -> [{block, column, columnCount}]`로 분리한다.

---

## 4. 데이터 모델

### Source 변경

```kotlin
// 삭제
var sortOrder: Int = 0

// 추가
var startMinutes: Int? = null   // 자정 기준 분. 240(04:00) ~ 1680(28:00)
```

`LocalTime`을 쓰지 않는 이유: 28:00을 표현할 수 없다. 자정 기준 분 정수가 04~28시 그리드와 1:1로 대응하고 위치 계산도 나눗셈 하나로 끝난다.

**불변식**: `scheduledDate`와 `startMinutes`는 항상 둘 다 null이거나 둘 다 값이 있다. `ScheduleService`에서 강제한다.

### 마이그레이션

`ddl-auto: update`는 컬럼을 추가만 하고 삭제하지 않는다. 엔티티에서 `sortOrder`를 제거하면 DB에 `SORT_ORDER NOT NULL` 컬럼이 남아 **새 소스 저장이 전부 실패한다.** 따라서 명시적 마이그레이션이 필요하다.

앱 시작 시 한 번 도는 멱등 마이그레이션 컴포넌트(`ScheduleTimeMigration`)를 만든다. Hibernate가 `START_MINUTES` 컬럼을 추가한 **뒤에** 실행되어야 하므로 `ApplicationRunner`로 등록한다.

1. `SORT_ORDER` 컬럼이 없으면 아무것도 하지 않고 종료 (멱등성)
2. 배정된 소스를 `scheduled_date`, `sort_order` 순으로 읽는다
3. 날짜별로 10:00(600분)부터 시작해 `직전 시작 + 직전 소요시간 + 30분`씩 이어붙여 `start_minutes`를 채운다. `durationMinutes`가 30의 배수가 아닐 수 있으므로 **계산 결과는 항상 30분 위로 올림**한다(5절의 30분 배수 규칙과 어긋나지 않게). 1680을 넘으면 1680으로 고정한다.
4. `ALTER TABLE SOURCE DROP COLUMN IF EXISTS SORT_ORDER`

이렇게 하면 기존에 짜둔 순서가 시각으로 변환되어 보존된다.

---

## 5. API

| 동작 | 현재 | 변경 후 |
|---|---|---|
| 배정/이동 | `POST /api/schedule/day/{date}` `{sourceIds:[...]}` | **삭제** |
| 배정/이동 | — | `PUT /api/schedule/{sourceId}` `{date, startMinutes}` |
| 해제 | `DELETE /api/schedule/{sourceId}` | 변경 없음 |

드래그 한 번 = 요청 한 번이라 대응이 단순하고, 실패 시 되돌릴 대상도 명확하다.

### ScheduleService

```kotlin
fun assign(sourceId: Long, date: LocalDate, startMinutes: Int)
fun remove(sourceId: Long)
```

`assign` 검증:
- `startMinutes` ∈ `[240, 1680]` — 벗어나면 `IllegalArgumentException("시간은 04:00~28:00 사이여야 합니다.")`
- `startMinutes % 30 == 0` — 아니면 `IllegalArgumentException("시간은 30분 단위여야 합니다.")`
- 소스가 없으면 `NoSuchElementException`

겹침은 검사하지 않는다.

`remove`는 `scheduledDate`와 `startMinutes`를 함께 null로 만든다.

기존 `assignDay`는 제거한다.

---

## 6. 드래그 인터랙션

이번 작업에서 가장 위험한 부분이다. HTML5 Drag and Drop API는 모바일에서 동작하지 않으므로 **Pointer Events로 직접 구현한다.** SortableJS 같은 라이브러리는 리스트 재정렬용이라 "시간 그리드에 좌표로 떨어뜨리기"에 맞지 않는다.

### 동작

1. 소스 카드/타임테이블 블록에 `pointerdown` → 6px 이상 이동하면 드래그 시작 (탭과 구분)
2. 드래그 중 손가락을 따라다니는 고스트 요소를 그린다. 드래그 요소에 `touch-action: none`을 걸어 브라우저 스크롤이 이벤트를 가로채지 않게 한다.
3. 타임테이블 위에서는 30분 스냅된 위치에 반투명 미리보기 블록을 그리고 `10:30 – 12:00` 라벨을 표시한다.
4. 포인터가 타임테이블 위/아래 가장자리 40px 안에 있으면 타임테이블을 자동 스크롤한다.
5. `pointerup`:
   - 타임테이블 위 → `PUT /api/schedule/{id}`
   - 왼쪽 레일 위 → `DELETE /api/schedule/{id}` (배정 해제)
   - 그 외 → 아무것도 하지 않는다
6. 요청 실패 시 이전 상태로 되돌리고 알린다. 401은 기존 `isUnauthorized` 처리를 따른다.

이미 배치된 블록도 같은 방식으로 이동할 수 있다.

### 폴백

드래그가 실패하거나 손이 불편한 상황을 위해, **블록을 탭하면 시각을 직접 고르는 하단 시트**를 연다. 시트에서 시각 변경과 배정 해제가 가능하다.

### 분리할 순수 함수

- `snapToSlot(clientY, gridRect, scrollTop) -> startMinutes`
- `layoutBlocks(blocks) -> [{block, column, columnCount}]`
- `formatSlot(minutes) -> "25:30"`

---

## 7. 테스트

### 자동 테스트 (Kotlin)

- `ScheduleServiceTest`
  - 배정 시 `scheduledDate`와 `startMinutes`가 함께 설정된다
  - 이미 배정된 소스를 다른 날짜/시각으로 이동한다
  - 해제 시 두 필드가 함께 null이 된다
  - `startMinutes` 239 / 1681 거부
  - `startMinutes` 615 (30분 배수 아님) 거부
  - 없는 소스 ID에 `NoSuchElementException`
  - 겹치는 시각 배정은 성공한다
- `ScheduleControllerIntegrationTest` — `PUT /api/schedule/{id}` 성공/검증실패(400)/해제, 기존 `POST .../day/{date}` 테스트 제거
- `ScheduleTimeMigrationTest` — `sort_order`가 있는 상태에서 날짜별 시작 시각이 10:00부터 소요시간+30분 간격으로 채워지고, 컬럼이 삭제되며, 두 번 실행해도 안전한지
- `PlanControllerIntegrationTest`, `SourceServiceTest` 등 `sortOrder` 참조분 수정
- `/day` 화면용 컨트롤러 통합 테스트

### 수동 확인 체크리스트

JS 테스트 러너는 도입하지 않으므로 아래는 브라우저에서 직접 확인한다. 폰 실기기 또는 devtools 모바일 에뮬레이션(터치 활성) 기준.

- [ ] 왼쪽 레일의 소스를 타임테이블로 드래그하면 스냅된 시각에 블록이 생긴다
- [ ] 드래그 중 미리보기 블록과 시각 라벨이 보인다
- [ ] 드래그 시작 시 페이지가 스크롤되지 않는다
- [ ] 타임테이블 가장자리에서 자동 스크롤된다
- [ ] 배치된 블록을 다른 시각으로 옮길 수 있다
- [ ] 블록을 왼쪽 레일로 끌면 배정이 해제된다
- [ ] 짧게 탭하면 드래그가 아니라 시각 수정 시트가 열린다
- [ ] 겹치는 두 블록이 가로로 나뉘어 표시된다
- [ ] 28:00을 넘기는 블록에 `→ 28:00+`가 표시된다
- [ ] 날짜를 바꾸면 타임테이블과 참고사항이 함께 바뀐다
- [ ] 지도 접기/펴기가 동작하고 소스 탭 시 지도가 이동한다
- [ ] `/day` 화면에 그 날 동선이 번호 순서대로 선으로 연결된다
- [ ] 다크모드에서 모든 화면이 읽힌다
- [ ] 하단 탭이 홈 인디케이터에 가리지 않는다

---

## 8. 손대는 파일

**변경**
- `source/Source.kt` — `sortOrder` 제거, `startMinutes` 추가
- `schedule/ScheduleService.kt` — `assignDay` → `assign`
- `schedule/ScheduleController.kt` — `POST .../day/{date}` → `PUT /api/schedule/{id}`
- `static/css/style.css` — 전면 재작성
- `static/js/plan.js` — 전면 재작성
- `static/js/sources.js` — 시트 UI에 맞게 수정
- `templates/fragments/layout.html` — 탭 3개
- `templates/plan/index.html`, `templates/sources/index.html` — 재작성
- `templates/auth/login.html`, `templates/auth/setup.html` — 새 디자인 적용

**신규**
- `schedule/ScheduleTimeMigration.kt`
- `dayview/DayViewController.kt`
- `templates/dayview/index.html`
- `static/js/dayview.js`

**테스트**: 위 7절 참고
