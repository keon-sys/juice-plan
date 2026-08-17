# 체크리스트(check) 섹션 설계

날짜: 2026-08-17

이 문서는 `2026-08-17-budget-section-design.md`의 후속이다. 그 스펙이 만든 섹션 구조(`Nav.kt`, `fragments/tabbar.html`, 지도 없는 독립 페이지)를 그대로 쓰고, 자리만 잡아둔 `check` 섹션의 내용을 채운다.

## 배경

`check` 섹션은 예산 작업 때 화살표 이동과 리다이렉트 구조를 완성하려고 만든 스텁이다. 탭 하나(`shopping`)에 "준비 중입니다" 카드만 있다.

여행 준비에는 성격이 다른 세 가지 목록이 필요하다. 현지에서 **살 것**, 집에서 **챙겨갈 것**, 출발 전에 **해둘 것**. 셋을 한 화면에 섞으면 "지금 뭘 해야 하나"를 읽어내기 어렵다.

## 목표

- `check` 섹션에 탭 셋(쇼핑 목록 · 준비물 · 할일)을 만든다.
- 각 목록에서 항목을 추가·수정·삭제하고 체크한다.
- 체크한 항목은 아래로 내려 남은 일이 위에 모이게 한다.

## 비목표

- 예산 섹션과의 연결. 체크한다고 지출이 생기지 않는다.
- 사람별 담당자 구분. 둘이 같이 보는 한 장짜리 목록이다.
- 마감일, 알림, 반복 항목.
- 손으로 순서 바꾸기(드래그 정렬).
- 초기 데이터. 세 목록 모두 빈 상태로 시작한다.

---

## 1. 라우팅

`Nav.kt`의 `check` 섹션에 탭 두 개를 더한다. 기본 탭은 `shopping` 그대로다.

| 순서 | 탭 | 아이콘 | 라벨 |
|---|---|---|---|
| 1 | `shopping` | 🛒 | 쇼핑 목록 |
| 2 | `packing` | 🎒 | 준비물 |
| 3 | `todo` | ✅ | 할일 |

서버 라우트는 이미 있는 `CheckPageController`가 그대로 처리한다(`/check` → `/check/shopping`, 모르는 탭 → 기본 탭). 탭이 늘어도 `section.has(tab)`이 알아서 받는다.

섹션 안의 탭 이동은 `check.js`가 `pushState`로 한다. `budget.js`와 같은 방식이다.

## 2. 데이터 모델

세 목록이 전부 같은 모양(이름 + 체크 + 메모)이므로 엔티티는 하나다. 목록별로 엔티티를 나누면 같은 코드를 세 번 쓰게 되고, 목록을 하나 더할 때마다 또 한 벌이 늘어난다.

```kotlin
/** 어느 탭의 항목인가. 값을 늘리면 탭이 하나 느는 것과 같다. */
enum class CheckList { SHOPPING, PACKING, TODO }

@Entity
class CheckItem(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    var list: CheckList,
    var name: String,
    var checked: Boolean = false,
    var memo: String? = null
)
```

enum은 `@Enumerated(EnumType.STRING) + @JdbcTypeCode(SqlTypes.VARCHAR)`로 저장한다. `Source.placeType`, `BudgetItem.category`와 같은 이유다 — H2 네이티브 `ENUM` 컬럼이 만들어지면 `ddl-auto: update`가 나중에 값을 넓혀주지 않는다.

정렬 컬럼은 두지 않는다(예산과 같은 판단). 순서는 조회할 때 정한다.

## 3. 정렬 규칙

**체크 안 한 것이 먼저, 그 안에서는 `id` 순.**

넣은 순서대로 아래에 쌓이고, 체크하면 그 자리에서 목록 맨 아래 구역으로 내려간다. 체크를 풀면 원래 있던 자리(같은 id 순서)로 돌아온다 — 순서를 따로 저장하지 않으므로 저절로 그렇게 된다.

손으로 순서를 바꾸는 기능은 만들지 않는다. 목록이 길어야 스무 줄이고, 드래그 정렬은 정렬 컬럼과 재배치 API를 함께 요구한다.

## 4. API

페이지 첫 로드에서는 API를 부르지 않는다. 예산 페이지가 `BUDGET_ITEMS`를 인라인으로 받는 것처럼 `CHECK_ITEMS`를 받는다. 세 탭의 항목을 한 번에 실어 보내고 클라이언트가 목록별로 나눈다 — 탭을 옮길 때 서버로 가지 않기 위해서다.

| 메서드 | 경로 | 용도 |
|---|---|---|
| `POST` | `/api/check/items` | 추가. 저장된 항목을 돌려준다 |
| `PUT` | `/api/check/items/{id}` | 이름·메모 수정 |
| `PUT` | `/api/check/items/{id}/checked` | 체크 토글 |
| `DELETE` | `/api/check/items/{id}` | 삭제 |

체크 토글에 전용 경로를 두는 이유는 이게 이 화면에서 압도적으로 잦은 동작이기 때문이다. 체크 한 번에 이름과 메모까지 통째로 보내면, 시트를 열지도 않은 채 이름을 덮어쓰는 셈이 된다.

### 검증

- `name`이 공백뿐이면 400. **예산과 다른 점이다** — 거기서는 이름 없이 카테고리만 잡아둔 자리가 의미를 가졌지만, 이름 없는 체크리스트 항목은 아무 뜻이 없다.
- `name`은 앞뒤 공백을 잘라 저장한다. `memo`는 비면 `null`.
- 없는 `id` 수정·토글·삭제는 404.

에러 본문은 기존 API와 같은 `{"error":"..."}` 모양이다. `config/GlobalExceptionHandler`가 `IllegalArgumentException` → 400, `NoSuchElementException` → 404로 이미 옮겨준다.

## 5. 화면

지도도 시트 핸들도 없는 독립 페이지다. 세 탭이 같은 렌더러를 쓰고 목록만 다르다.

```
┌────────────────────────────────┐
│ ＋ [ 항목 추가              ]  │  ← 엔터로 추가, 포커스 유지
└────────────────────────────────┘
  3/7 완료

  ☐  시로이코이비토
       회사 사람들 것까지
  ☐  로이스 생초콜릿
  ☐  돈키호테 파스

  ☑  ~~공항 면세점 사케~~
  ☑  ~~약국 상비약~~
```

- **입력 한 줄이 맨 위에 고정**된다. 시트를 열고 닫는 것보다 빠르다 — 체크리스트는 한 번에 여러 개를 몰아 넣는 화면이다. 엔터를 치면 항목이 추가되고 입력칸은 비워진 채 포커스를 유지해 다음 항목을 이어서 칠 수 있다.
- 체크박스를 누르면 즉시 저장된다. 저장을 기다리지 않고 화면을 먼저 바꾸지는 않는다 — 실패했는데 체크된 것처럼 보이면 안 된다.
- 이름을 누르면 편집 시트가 올라온다(이름·메모·삭제). 예산 시트와 같은 `.sheet` / `.sheet__backdrop` 클래스를 쓴다.
- 체크한 항목은 이름에 취소선을 긋고 흐리게 하며, 목록 아래 구역으로 내려간다.
- 목록 위에 `3/7 완료`를 작게 둔다. 탭바에 개수를 붙이지 않는 이유는 탭바가 이미 화살표 둘과 탭 셋으로 좁기 때문이다.
- 목록이 비면 "아직 항목이 없습니다" 한 줄.

## 6. 파일

### 새로 만드는 파일

```
src/main/kotlin/com/juiceplan/check/CheckItem.kt          엔티티 + enum
src/main/kotlin/com/juiceplan/check/CheckItemRepository.kt
src/main/kotlin/com/juiceplan/check/CheckService.kt
src/main/kotlin/com/juiceplan/check/CheckApiController.kt
src/main/resources/static/js/check-list.js                목록 렌더 + 추가 + 토글 + 시트
src/main/resources/static/js/check.js                     섹션 내 라우팅
```

### 고치는 파일

| 파일 | 이유 |
|---|---|
| `nav/Nav.kt` | check 탭 2개 추가 |
| `check/CheckPageController.kt` | 서비스 주입, `CHECK_ITEMS` 인라인 |
| `templates/check/index.html` | 스텁 → 세 뷰 컨테이너 + 입력줄 + 시트 |
| `static/js/api.js` | 체크 API 메서드 4개 |
| `static/css/style.css` | 체크리스트 행, 체크박스, 입력줄 |

## 7. 테스트

- `CheckServiceTest` — 추가·수정·삭제·토글, 빈 이름 거부, 이름 앞뒤 공백 제거, 목록별 필터, 정렬(체크한 것이 뒤, 그 안에서 id 순), 없는 id
- `CheckApiIntegrationTest` — API 4종 왕복, 빈 이름 400, 없는 id 404
- `SectionNavigationIntegrationTest` 추가 — `/check/packing`·`/check/todo`가 200, 탭 셋이 렌더, `/check` → `/check/shopping` 유지
- `NavTest`는 그대로 통과해야 한다(기본 탭이 탭 목록 안에 있는지 검사한다)

## 8. 위험

- **탭이 셋으로 늘면 탭바에 화살표 둘 + 탭 셋이 들어간다.** `schd`와 같은 밀도라 새로운 문제는 아니지만, `check`는 이제 `‹`만 활성이고 `›`는 비활성이라 폭이 한 칸 남는다.
- **체크 토글이 잦은데 매번 서버 왕복이다.** 개인용 로컬 앱이라 감수한다. 느껴질 만큼 느리면 화면을 먼저 바꾸고 실패 시 되돌리는 방식으로 바꾼다.
