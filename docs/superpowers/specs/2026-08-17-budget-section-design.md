# 예산(budget) 섹션 + 섹션 단위 하단 내브 설계

날짜: 2026-08-17

이 문서는 `2026-08-15-single-shell-map-design.md`의 후속이다. 그 스펙이 만든 단일 셸(`/schd/{tab}`)은 그대로 두고, 그 옆에 예산 섹션 `/budget`을 새로 만든다. 하단 탭바는 **섹션 안의 탭 이동**과 **섹션 사이 이동**을 함께 다루도록 구조를 바꾼다.

## 배경

지금 앱은 화면이 `schd` 섹션 하나뿐이고 하단 탭바는 그 안의 세 탭(장소 추가 / 동선 변경 / 계획 보기)만 오간다. 여행 준비에는 일정 말고도 예산과 체크리스트가 필요한데, 탭바에 탭을 계속 늘리면 한 줄에 여섯~일곱 개가 들어가 모바일에서 못 쓴다.

그래서 화면을 **섹션 → 탭** 2단으로 나눈다. 탭바 가운데는 지금 섹션의 탭만 보여주고, 양 끝의 작은 `‹`/`›` 버튼이 섹션을 옮긴다.

## 목표

- 섹션 3개(`schd`, `budget`, `check`)와 그 사이를 오가는 `‹`/`›` 내브를 만든다.
- 예산 섹션의 두 탭(`summary`, `list`)을 구현한다.
- 지출 항목을 화면에서 추가·수정·삭제한다.
- 기존에 쓰던 21개 항목을 첫 실행 때 자동으로 채운다.

## 비목표

- `check` 섹션의 실제 내용. 이번에는 "준비 중" 문구만 있는 스텁이다.
- 지출과 일정(`Source`)의 연결. 예산 항목은 독립적이다.
- 실시간 환율 조회. 환율은 사람이 손으로 넣는다.
- 인원수 설정. 2인으로 고정한다.
- 영수증 사진, 항목별 결제일, 통화 3종 이상.

---

## 1. 라우팅

### 섹션 정의

섹션 목록과 각 섹션의 탭·기본 탭은 `nav/Nav.kt` 한 곳에만 둔다.

| 순서 | 섹션 | 탭 (표시 순서) | 기본 탭 |
|---|---|---|---|
| 1 | `schd` | 장소 추가 · 동선 변경 · 계획 보기 | `day` |
| 2 | `budget` | 예산 요약 · 지출 내역 | `summary` |
| 3 | `check` | 쇼핑 목록 | `shopping` |

`schd`는 탭 표시 순서(`add`, `plan`, `day`)와 기본 탭(`day`)이 다르므로 기본 탭은 별도 필드로 갖는다.

```kotlin
data class NavTab(val id: String, val icon: String, val label: String)
data class NavSection(val id: String, val defaultTab: String, val tabs: List<NavTab>)
```

`Nav`는 `section(id)`, `prevPath(id)`, `nextPath(id)`를 제공한다. 경계에서는 `prevPath`/`nextPath`가 `null`이다.

### 서버 라우트

| 경로 | 동작 |
|---|---|
| `/`, `/schd` | → `/schd/day` |
| `/schd/{tab}` | 셸. 모르는 탭이면 → `/schd/day` |
| `/budget` | → `/budget/summary` |
| `/budget/{tab}` | 예산 페이지. 모르는 탭이면 → `/budget/summary` |
| `/check` | → `/check/shopping` |
| `/check/{tab}` | 스텁 페이지. 모르는 탭이면 → `/check/shopping` |

`ShellController`가 이미 쓰는 규칙(`tab !in TABS`면 기본 탭으로 리다이렉트)과 같다.

`‹`/`›`는 **섹션 루트**(`/budget`, `/check`)로 링크한다. 기본 탭이 무엇인지는 서버 리다이렉트가 결정하므로, 기본 탭 지식이 링크마다 흩어지지 않는다.

### 섹션 사이 이동은 전체 페이지 로드다

`schd` 셸이 탭 이동을 `pushState`로 하는 이유는 페이지를 다시 그리면 구글맵이 통째로 재생성되기 때문이다. `budget`은 다른 템플릿이므로 `‹`/`›`를 누르면 전체 페이지가 로드되고, `schd`로 돌아올 때 지도가 새로 만들어진다.

이 비용을 알면서 받아들인다. 대안(단일 셸에 예산 뷰까지 넣기)은 지도가 필요 없는 화면에서도 지도를 살려두어야 하고, 셸의 시트 핸들·레이아웃 예외 처리가 계속 늘어난다. 섹션 이동은 자주 일어나지 않는다.

섹션 **안**의 탭 이동은 지금처럼 `pushState`다. 예산 섹션도 같은 방식이라 두 탭을 오갈 때 서버로 가지 않는다.

### 탭바 프래그먼트

`templates/fragments/tabbar.html`에 프래그먼트 하나를 두고 세 섹션이 모두 쓴다. `shell/index.html`에 하드코딩된 `<footer>`도 이걸로 바꾼다.

컨트롤러가 모델에 넣는 값: `section`, `activeTab`, `prevPath`, `nextPath`.

```html
<footer th:fragment="tabbar">
  <nav>
    <a class="nav-arrow" th:if="${prevPath}" th:href="@{${prevPath}}" aria-label="이전 화면">‹</a>
    <span class="nav-arrow nav-arrow--off" th:unless="${prevPath}" aria-hidden="true">‹</span>

    <a th:each="t : ${section.tabs}"
       th:href="@{'/' + ${section.id} + '/' + ${t.id}}"
       th:attr="data-tab=${t.id}"
       th:classappend="${t.id == activeTab} ? 'active'">
      <span aria-hidden="true" th:text="${t.icon}"></span><span th:text="${t.label}"></span>
    </a>

    <a class="nav-arrow" th:if="${nextPath}" th:href="@{${nextPath}}" aria-label="다음 화면">›</a>
    <span class="nav-arrow nav-arrow--off" th:unless="${nextPath}" aria-hidden="true">›</span>
  </nav>
</footer>
```

화살표에는 `data-tab`이 없다. 그래서 클릭을 가로채는 기존 선택자(`footer nav a[data-tab]`)에 걸리지 않고 진짜 링크로 동작한다. 활성 탭을 칠하는 코드도 `data-tab` 비교라 화살표는 절대 활성이 되지 않는다.

`footer nav a`가 `flex: 1`이라 화살표가 탭과 같은 폭을 먹는다. `.nav-arrow { flex: 0 0 36px; }`로 좁힌다. 비활성 화살표는 `<a>`가 아니라 `<span>`이라 눌리지 않고, `opacity`를 낮춰 흐리게 둔다. 자리는 그대로 차지해야 탭 위치가 섹션마다 흔들리지 않는다.

---

## 2. 데이터 모델

```kotlin
enum class BudgetCategory { FLIGHT, HOTEL, FOOD, TRANSIT, ACTIVITY, SHOPPING, ETC }
enum class PaymentMethod { CREDIT_CARD, TRAVEL_LOG, CASH }
enum class SettlementStatus { PENDING, DONE, NOT_APPLICABLE }
enum class Currency { JPY, KRW }

@Entity
class BudgetItem(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    var name: String,                  // 빈 문자열 허용
    var category: BudgetCategory,
    var paymentMethod: PaymentMethod,
    var currency: Currency,            // 결제 통화
    var amount: Int,                   // 2인 총액, 결제 통화 기준
    var settlement: SettlementStatus,
    var memo: String? = null
)
```

모든 enum은 `@Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)`로 저장한다. `Source.placeType` 주석에 적힌 이유 그대로다 — Hibernate가 H2에서 enum을 네이티브 `ENUM(...)` 컬럼으로 만들면 `ddl-auto: update`가 나중에 값을 늘려주지 않아, 종류를 하나 더할 때마다 기존 DB가 새 값을 거부한다.

정렬 컬럼은 두지 않는다. 목록은 카테고리(enum 선언 순) → `id` 순으로 낸다.

### 이름이 빈 항목

지금 쓰는 표에는 이름 없이 카테고리와 결제 수단만 잡아둔 식비 10건이 있다. "몇 끼를 어떤 수단으로 낼지"만 정해둔 자리다. `name`에 빈 문자열을 허용하고, 화면에서는 `(이름 없음)`을 흐리게 보여준다.

### 환율

```kotlin
@Entity
class BudgetSetting(
    @Id val id: Long = 1,              // 항상 한 행
    var ratePer100Jpy: Int             // 100엔당 원. 기본 900
)
```

테이블 이름은 `BUDGET_SETTING`이다. 예전 인증이 쓰던 `APP_SETTINGS`는 `SchemaMigration.dropAppSettings()`가 매 부팅마다 `DROP TABLE IF EXISTS`로 지우므로 그 이름은 절대 재사용하지 않는다.

**단위는 화면·API·DB 전부 "100엔당 원"인 정수 하나다.** 사람이 환율을 말할 때 쓰는 단위가 그대로 저장되므로 어디서도 단위를 바꿔 담을 일이 없다. 환산은 정수·`BigDecimal` 연산이라 `Double` 오차가 끼지 않는다.

```kotlin
fun jpyToKrw(jpy: Int, ratePer100Jpy: Int): Int =
    BigDecimal(jpy).multiply(BigDecimal(ratePer100Jpy))
        .divide(BigDecimal(100))
        .setScale(0, RoundingMode.HALF_UP)
        .toInt()
```

---

## 3. 계산 규칙

순수 함수만 모은 `BudgetTotals`로 분리해 DB 없이 단위 테스트한다.

**항목은 결제 통화 그대로 저장하고 표시한다. 환산값은 저장하지 않는다.**

- 카테고리 합계는 **통화별로 따로** 낸다. 한 카테고리에 엔·원이 섞이면 두 값을 모두 갖는다.
- 1인당 = 그 통화의 2인 합계 ÷ 2, 나머지는 버린다(내림).
- 환율은 **총액 한 줄과 파이차트 비중 계산에만** 쓴다.
  - 환산 총액 = `KRW 합계 + jpyToKrw(JPY 합계, rate)`
  - 차트 비중 = 카테고리별 환산 원화 / 환산 총액

카테고리 단위로 먼저 환산한 뒤 더한다. 항목마다 반올림하면 카테고리 합계와 총액이 1원씩 어긋난다.

환산 총액이 0이면 차트를 그리지 않고 "아직 금액이 없습니다"를 보여준다.

```kotlin
/** 통화별 2인 총액. 한 카테고리에 엔·원이 섞이면 둘 다 0이 아니다. */
data class Money(val jpy: Int, val krw: Int)

data class CategoryTotal(
    val category: BudgetCategory,
    val count: Int,
    val currencies: List<Currency>, // 이 카테고리에 실제로 쓰인 결제 통화
    val total: Money,
    val perPerson: Money,
    val convertedKrw: Int           // 차트 비중용
)

data class BudgetSummary(
    val rows: List<CategoryTotal>,  // 항목이 0건인 카테고리는 뺀다
    val count: Int,
    val currencies: List<Currency>,
    val total: Money,
    val perPerson: Money,
    val ratePer100Jpy: Int,
    val convertedTotalKrw: Int,
    val convertedPerPersonKrw: Int
)
```

`currencies`가 따로 필요한 이유는 금액이 전부 0인 카테고리 때문이다. 식비 10건이 모두 엔화 0원이면 `Money(0, 0)`만으로는 `¥0`으로 적을지 `₩0`으로 적을지 알 수 없다. 어떤 통화가 쓰였는지는 합계가 아니라 항목이 안다.

---

## 4. API

페이지 첫 로드에서는 API를 부르지 않는다. `shell/index.html`이 `SOURCES`를 인라인으로 받는 것과 같이, `budget/index.html`도 `BUDGET_ITEMS`와 `BUDGET_SUMMARY`를 인라인 스크립트로 받는다.

| 메서드 | 경로 | 용도 |
|---|---|---|
| `GET` | `/api/budget/summary` | 요약 다시 계산 |
| `POST` | `/api/budget/items` | 추가. 저장된 항목을 돌려준다 |
| `PUT` | `/api/budget/items/{id}` | 수정 |
| `DELETE` | `/api/budget/items/{id}` | 삭제 |
| `PUT` | `/api/budget/rate` | 환율 변경. 새 요약을 돌려준다 |

항목 응답으로 엔티티를 돌려주는 이유는 `SourceController`와 같다 — 클라이언트가 생성된 `id`를 알아야 목록에 넣고 이후 수정·삭제를 걸 수 있다.

**합계는 서버만 계산한다.** 항목을 고친 뒤에는 `GET /api/budget/summary`로 다시 받아온다. 왕복이 한 번 늘지만, 3절의 계산 규칙(통화별 분리, 내림, 카테고리 단위 환산)이 JS에 복제되지 않는다. 이 프로젝트에는 JS 테스트 장치가 없으므로, 복제된 쪽은 아무도 지켜주지 않는다.

카드에 찍는 항목별 1인당 금액(`amount / 2`)만 JS가 직접 나눈다. 나눗셈 한 번이라 복제로 치지 않는다.

### 검증

- `amount` < 0 이면 400
- `name`은 공백만 있어도 통과(빈 이름 허용). 앞뒤 공백은 잘라 저장한다
- `ratePer100Jpy` <= 0 이면 400
- 없는 `id` 수정·삭제는 404

에러 본문은 기존 API와 같은 `{"error":"..."}` 모양이라 `Api.send`가 메시지를 그대로 띄운다.

---

## 5. 화면 — `/budget/summary`

지도도 시트 핸들도 없다. 그냥 세로로 스크롤되는 본문과 하단 탭바뿐이다.

```
┌────────────────────────────────┐
│ 100엔 = ₩ [  900 ]              │  ← 고치면 즉시 저장·재계산
│                                │
│         ╭───────╮              │
│         │   ◕   │  대략         │
│         ╰───────╯  ₩1,007,520  │
│                                │
│  ● 항공 85%  ● 교통 12%  ● 관광 3% │
└────────────────────────────────┘
```

환율은 `100엔 = ₩___` 형태로 받는다. 사람이 환율을 말할 때 쓰는 단위이고, `0.09` 같은 소수를 넣게 하는 것보다 오타가 덜 난다. 화면에 넣은 정수가 그대로 저장된다.

도넛은 외부 라이브러리 없이 인라인 SVG로 그린다. 이 프로젝트가 구글맵 말고는 CDN을 쓰지 않는다는 원칙과 맞고, 색은 `style.css`의 디자인 토큰을 그대로 쓸 수 있어 다크모드가 저절로 따라온다. 카테고리 7개용 색 토큰(`--cat-flight` 등)을 토큰 블록에 추가한다.

### 표

| 카테고리 | 건수 | 2인 합계 | 1인당 |
|---|---|---|---|
| 항공 (Flight) | 1 | ₩853,800 | ₩426,900 |
| 숙박 (Hotel) | 1 | ₩0 | ₩0 |
| 식비 (Food & Dining) | 10 | ¥0 | ¥0 |
| 교통 (Transit) | 4 | ¥13,480 | ¥6,740 |
| 관광/입장료 (Activities) | 1 | ¥3,600 | ¥1,800 |
| 쇼핑/기념품 (Shopping) | 2 | ¥0 | ¥0 |
| 기타 (eSIM/보험 등) | 2 | ₩0 | ₩0 |
| **합계** | **21** | **¥17,080**<br>**₩853,800** | **¥8,540**<br>**₩426,900** |

금액 칸은 **0이 아닌 통화만** 보여준다. 두 통화가 다 0이면 그 통화 하나만 `¥0`/`₩0`으로 보여준다. 엔·원이 섞인 카테고리는 두 줄로 쌓는다.

표 아래 한 줄:

> 100엔 = ₩900 기준 **대략 ₩1,007,520** · 1인 ₩503,760

"대략"을 붙이는 이유는 이 숫자가 저장된 값이 아니라 환율을 곱해 만든 참고값이기 때문이다.

원래 쓰던 표에는 카테고리마다 환산 원화 열이 있었지만 없앴다. 모바일 폭에서 5열은 읽기 어렵고, 카테고리별 환산값은 실제로 결제할 금액이 아니다.

---

## 6. 화면 — `/budget/list`

카테고리 그룹 헤더 + 항목 카드. 8열 표를 모바일에 밀어 넣지 않는다.

```
─ 교통 (Transit) · 4건 · ¥13,480 ──────
┌──────────────────────────────┐
│ 신치토세공항-스스키노 왕복 (2인)   │
│ 트래블로그 · 미정산               │
│ ¥5,440    1인 ¥2,720            │
│ JR 쾌속에어포트 + 지하철          │
└──────────────────────────────┘
```

- 그룹 헤더의 합계도 통화별로 따로 낸다.
- 정산 상태는 색이 다른 작은 배지다. 미정산은 기본색, 완료는 `--attraction`, 해당없음은 `--muted`.
- 비고가 없으면 그 줄을 그리지 않는다.
- 우하단 `+` FAB로 추가. 카드를 탭하면 편집 시트가 올라온다. FAB·시트·백드롭 CSS는 셸의 `sourceSheet`와 같은 클래스를 쓴다.

### 편집 시트

```
이름      [ 신치토세공항-스스키노 왕복 (2인) ]
카테고리   [ 교통 (Transit)          ▾ ]
결제 수단  [ 트래블로그              ▾ ]
금액      [ ¥ ][ ₩ ]  [        5,440 ]
                       1인 ¥2,720
정산      ( ) 미정산  ( ) 완료  ( ) 해당없음
비고      [ JR 쾌속에어포트 + 지하철       ]

          [        저장        ]
          [        삭제        ]   ← 수정할 때만
```

- 통화는 토글 두 개 중 하나다. 한 항목에 엔과 원을 같이 적을 수 없다.
- 금액 아래 회색 줄은 저장되지 않는 1인당 미리보기다. 통화를 바꾸면 같이 바뀐다.
- 삭제는 한 번 더 확인받는다.

---

## 7. 화면 — `/check/shopping` (스텁)

`budget`과 같은 껍데기에 "준비 중입니다" 카드 하나. 존재 이유는 `›` 이동과 리다이렉트 구조를 이번에 완성해 두는 것이다.

---

## 8. 초기 데이터

`BudgetSeeder`(`ApplicationRunner`)가 `BUDGET_ITEM`이 **비어 있을 때만** 21개 항목과 환율 900(100엔 = ₩900)을 넣는다. `SchemaMigration`과 같은 패턴이라 몇 번을 띄워도 안전하고, 사람이 지운 항목이 되살아나지 않는다.

금액이 0인 항목의 통화는 **앞으로 실제로 결제할 통화**로 넣는다. 일본에서 쓸 식비·쇼핑은 엔화, 한국에서 결제하는 숙박·eSIM·보험은 원화다. 금액이 0이라 어떤 합계도 달라지지 않고, 화면에서 바로 고칠 수 있다.

| # | 이름 | 카테고리 | 결제 수단 | 통화 | 금액 | 정산 | 비고 |
|---|---|---|---|---|---|---|---|
| 1 | 왕복 항공권 (2인) | 항공 | 신용카드 | KRW | 853800 | 해당없음 | 이스타항공 사전 결제 완료(각자 결제) |
| 2 | 코코 호텔 스스키노 (5박, 2인) | 숙박 | 신용카드 | KRW | 0 | 미정산 | 조식 미포함, 스스키노역 근처 |
| 3 | 신치토세공항-스스키노 왕복 교통 (2인) | 교통 | 트래블로그 | JPY | 5440 | 미정산 | JR 쾌속에어포트 + 지하철 |
| 4 | 오타루 왕복 JR 열차 (2인) | 교통 | 트래블로그 | JPY | 3000 | 미정산 | JR 하코다테선 지정석/자유석 |
| 5 | 주말 도니치카 패스 1일권 (2인) | 교통 | 현금 | JPY | 1040 | 미정산 | 2일차 일요일 지하철 무제한 |
| 6 | 기타 시내 지하철/버스 충전 (2인) | 교통 | 트래블로그 | JPY | 4000 | 미정산 | IC카드 충전식 사용 |
| 7 | (없음) | 식비 | 트래블로그 | JPY | 0 | 미정산 | |
| 8 | (없음) | 식비 | 트래블로그 | JPY | 0 | 미정산 | |
| 9 | (없음) | 식비 | 현금 | JPY | 0 | 미정산 | |
| 10 | (없음) | 식비 | 트래블로그 | JPY | 0 | 미정산 | |
| 11 | (없음) | 식비 | 트래블로그 | JPY | 0 | 미정산 | |
| 12 | (없음) | 식비 | 신용카드 | JPY | 0 | 미정산 | |
| 13 | (없음) | 식비 | 트래블로그 | JPY | 0 | 미정산 | |
| 14 | (없음) | 식비 | 현금 | JPY | 0 | 미정산 | |
| 15 | (없음) | 식비 | 트래블로그 | JPY | 0 | 미정산 | |
| 16 | 4일차 오타루 운하 크루즈 (2인) | 관광 | 트래블로그 | JPY | 3600 | 미정산 | 운하 크루즈 탑승권 |
| 17 | (없음) | 식비 | 트래블로그 | JPY | 0 | 미정산 | |
| 18 | 돈키호테 쇼핑 & 기념품 | 쇼핑 | 신용카드 | JPY | 0 | 미정산 | 의약품, 화장품, 소품 |
| 19 | 공항 면세점 과자/사케 선물 | 쇼핑 | 신용카드 | JPY | 0 | 미정산 | 시로이코이비토, 로이스 |
| 20 | 일본 eSIM 6일권 (2인) | 기타 | 신용카드 | KRW | 0 | 완료 | 매일 2GB eSIM |
| 21 | 해외 여행자 보험 (2인) | 기타 | 신용카드 | KRW | 0 | 완료 | 기본 플랜 |

4번 비고의 노선명은 원본에 "학콘선"으로 적혀 있었으나 하코다테선(函館本線)의 오기로 보아 바로잡았다.

시드 순서는 원본 표 순서 그대로다. 화면에서는 카테고리별로 묶이므로 17번 식비가 16번 뒤에 있어도 식비 그룹 안에 들어간다.

---

## 9. 파일

### 새로 만드는 파일

```
src/main/kotlin/com/juiceplan/nav/Nav.kt
src/main/kotlin/com/juiceplan/budget/BudgetItem.kt          엔티티 + enum 4개
src/main/kotlin/com/juiceplan/budget/BudgetItemRepository.kt
src/main/kotlin/com/juiceplan/budget/BudgetSetting.kt       + Repository
src/main/kotlin/com/juiceplan/budget/BudgetTotals.kt        순수 계산
src/main/kotlin/com/juiceplan/budget/BudgetService.kt
src/main/kotlin/com/juiceplan/budget/BudgetPageController.kt
src/main/kotlin/com/juiceplan/budget/BudgetApiController.kt
src/main/kotlin/com/juiceplan/budget/BudgetSeeder.kt
src/main/kotlin/com/juiceplan/check/CheckPageController.kt

src/main/resources/templates/fragments/tabbar.html
src/main/resources/templates/budget/index.html
src/main/resources/templates/check/index.html

src/main/resources/static/js/donut.js                       SVG 도넛
src/main/resources/static/js/budget-summary.js
src/main/resources/static/js/budget-list.js
src/main/resources/static/js/budget.js                      섹션 내 라우팅
```

### 고치는 파일

| 파일 | 이유 |
|---|---|
| `shell/index.html` | 하드코딩된 `<footer>`를 탭바 프래그먼트로 교체 |
| `ShellController.kt` | `section`/`activeTab`/`prevPath`/`nextPath` 모델 값 추가, 탭 목록을 `Nav`에서 가져오기 |
| `style.css` | 화살표, 예산 표, 카드, 배지, 카테고리 색 토큰 |
| `api.js` | 예산 API 메서드 4개 추가 |

`shell.js`의 `TABS`/`DEFAULT_TAB`/`BASE` 상수는 그대로 둔다. 서버의 `Nav`와 중복이지만, 셸의 라우팅은 지도 재생성을 피하려는 클라이언트 쪽 사정이라 서버 설정을 내려받게 엮을 이유가 없다.

---

## 10. 테스트

TDD로 간다. 각 항목은 실패하는 테스트를 먼저 쓴다.

**`BudgetTotalsTest`** — DB 없이 도는 순수 계산 테스트. 위 21개 항목을 골든 데이터로 넣고 검증한다.

- 카테고리별 건수·통화별 합계가 5절 표와 일치
- 1인당은 내림 (`¥1,041 → ¥520`)
- 환율 900에서 환산 총액 `₩1,007,520`, 1인 `₩503,760`
- 엔·원이 섞인 카테고리가 두 통화 값을 모두 갖는다
- 항목이 0건인 카테고리는 행에 없다
- 총액 0이면 차트 비중을 계산하지 않는다 (0으로 나누지 않는다)

**`BudgetServiceTest`**

- 추가·수정·삭제
- 환율을 바꿔도 저장된 `amount`는 그대로고 환산 총액만 바뀐다
- 음수 금액, 0 이하 환율은 거부
- 없는 id 수정·삭제는 예외

**`NavTest`**

- `schd`의 `prevPath`는 null, `check`의 `nextPath`는 null
- `budget`의 앞뒤는 `/schd`, `/check`
- 모든 섹션의 `defaultTab`이 그 섹션 탭 목록 안에 있다

**`BudgetControllerIntegrationTest`**

- `/budget` → 302 `/budget/summary`
- `/budget/xyz` → 302 `/budget/summary`
- `/check` → 302 `/check/shopping`
- `/budget/list` 200
- API 4종 왕복, 검증 실패 시 400/404

**`BudgetSeederTest`**

- 빈 DB에 21개가 들어간다
- 이미 항목이 있으면 아무것도 하지 않는다

기존 `ShellControllerIntegrationTest`는 그대로 통과해야 한다. 탭바를 프래그먼트로 바꾸면서 `/schd/*` 응답이 깨지지 않았는지 확인하는 안전망이다.

---

## 11. 위험

- **탭바 프래그먼트 교체가 셸을 건드린다.** `shell.js`가 `footer nav a[data-tab]`로 링크를 찾으므로 프래그먼트가 그 속성을 반드시 내야 한다. 화살표에는 그 속성이 없어야 한다. 통합 테스트로 `/schd/day` 응답에 세 탭 링크가 있는지 확인한다.
- **항목을 고칠 때마다 요약을 다시 받아온다.** 왕복이 한 번 늘지만 계산이 한 곳에만 있는 값이 더 크다. 느껴질 만큼 느리면 변경 API가 새 요약을 함께 돌려주도록 바꾼다.
