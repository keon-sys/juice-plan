# 예산(budget) 섹션 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 하단 탭바를 섹션 2단 구조로 바꾸고, 지출을 통화별로 관리하는 예산 섹션(`/budget/summary`, `/budget/list`)을 만든다.

**Architecture:** 섹션 정의(`nav/Nav.kt`)를 서버 한 곳에 두고 Thymeleaf 프래그먼트 하나가 세 섹션의 탭바를 그린다. 예산은 지도가 없는 독립 페이지라 셸(`shell/index.html`)을 건드리지 않고, 섹션 안의 탭 이동만 `pushState`로 한다. 합계 계산은 순수 함수 `BudgetTotals` 한 곳에만 있고 JS는 서버가 준 요약을 그리기만 한다.

**Tech Stack:** Kotlin 1.9.24 / Spring Boot 3.3.2 (Web, Thymeleaf, Data JPA) / H2 파일 DB / 바닐라 JS (빌드 도구 없음) / JUnit 5 + MockMvc

**Spec:** `docs/superpowers/specs/2026-08-17-budget-section-design.md`

## Global Constraints

- **JS 빌드 도구도 npm도 없다.** 모든 프런트 코드는 `src/main/resources/static/js/` 아래 평범한 `<script>` 파일이고, 전역 객체(`window.Xxx`) + IIFE 패턴을 따른다. 외부 CDN은 구글맵 말고 쓰지 않는다.
- **JS 테스트 장치가 없다.** 따라서 검증이 필요한 로직은 반드시 Kotlin 쪽에 둔다.
- **모든 JPA enum 필드는 `@Enumerated(EnumType.STRING)` + `@JdbcTypeCode(SqlTypes.VARCHAR)`.** H2에서 네이티브 `ENUM` 컬럼이 만들어지면 `ddl-auto: update`가 나중에 값을 못 늘려 기존 DB가 새 값을 거부한다.
- **CSS는 `style.css` 상단 토큰(`--bg`, `--surface`, `--text`, `--muted`, `--border`, `--primary`, `--radius`, `--tabbar-h` 등)만 참조한다.** 색을 직접 쓰면 다크모드에서 깨진다.
- **인원은 2인 고정.** 1인당 = 2인 총액 ÷ 2, 나머지는 버린다.
- **환율 단위는 화면·API·DB 전부 "100엔당 원"인 정수 하나다.** 기본값 900.
- **테스트 실행:** `./gradlew test`. 단일 클래스는 `./gradlew test --tests "com.juiceplan.budget.BudgetTotalsTest"`.
- **에러 규약:** 서비스가 `require(...)`로 던진 `IllegalArgumentException` → 400, `NoSuchElementException` → 404. `config/GlobalExceptionHandler`가 이미 둘 다 `{"error":"..."}` 로 바꿔준다. 컨트롤러에 예외 처리를 새로 쓰지 않는다.
- **커밋 메시지는 한국어**, `feat:` / `test:` / `refactor:` 접두어를 쓴다 (기존 이력과 같게).

---

## 파일 구조

### 새로 만드는 파일

| 파일 | 책임 |
|---|---|
| `src/main/kotlin/com/juiceplan/nav/Nav.kt` | 섹션·탭 목록, 이웃 섹션 경로, 모델 주입 헬퍼 |
| `src/main/kotlin/com/juiceplan/budget/BudgetItem.kt` | 지출 항목 엔티티 + enum 4개 |
| `src/main/kotlin/com/juiceplan/budget/BudgetSetting.kt` | 환율 한 행짜리 엔티티 |
| `src/main/kotlin/com/juiceplan/budget/BudgetRepositories.kt` | 두 리포지토리 인터페이스 |
| `src/main/kotlin/com/juiceplan/budget/BudgetTotals.kt` | 합계 계산 (순수 함수, DB·스프링 모름) |
| `src/main/kotlin/com/juiceplan/budget/BudgetService.kt` | CRUD + 환율 저장 + 요약 조립 |
| `src/main/kotlin/com/juiceplan/budget/BudgetPageController.kt` | `/budget/{tab}` 페이지 |
| `src/main/kotlin/com/juiceplan/budget/BudgetApiController.kt` | `/api/budget/**` |
| `src/main/kotlin/com/juiceplan/budget/BudgetSeeder.kt` | 빈 DB에 21개 항목 주입 |
| `src/main/kotlin/com/juiceplan/check/CheckPageController.kt` | `/check/{tab}` 스텁 페이지 |
| `src/main/resources/templates/fragments/tabbar.html` | 세 섹션 공용 하단 탭바 |
| `src/main/resources/templates/budget/index.html` | 예산 페이지 |
| `src/main/resources/templates/check/index.html` | 체크리스트 스텁 |
| `src/main/resources/static/js/budget-types.js` | 카테고리·결제수단·정산 이름과 색 토큰 |
| `src/main/resources/static/js/donut.js` | SVG 도넛 |
| `src/main/resources/static/js/budget-summary.js` | 요약 탭 |
| `src/main/resources/static/js/budget-list.js` | 내역 탭 |
| `src/main/resources/static/js/budget.js` | 예산 섹션 라우팅 |

### 고치는 파일

| 파일 | 이유 |
|---|---|
| `src/main/kotlin/com/juiceplan/shell/ShellController.kt` | 탭 목록·기본 탭을 `Nav`에서 가져오고 탭바 모델 값을 넣는다 |
| `src/main/resources/templates/shell/index.html` | 하드코딩된 `<footer>` → 탭바 프래그먼트 |
| `src/main/resources/static/css/style.css` | 화살표, 섹션 본문 레이아웃, 예산 표·카드·배지, 카테고리 색 토큰 |
| `src/main/resources/static/js/api.js` | 예산 API 메서드 |

### 지우는 파일

| 파일 | 이유 |
|---|---|
| `src/main/resources/templates/fragments/layout.html` | 아무 데서도 참조하지 않는 죽은 프래그먼트다. 없어진 경로(`/sources`, `/plan`, `/day`)와 옛 탭 이름을 담고 있어, 새 탭바 프래그먼트와 나란히 두면 어느 쪽이 진짜인지 헷갈린다 |

---

## Task 1: 섹션 정의

**Files:**
- Create: `src/main/kotlin/com/juiceplan/nav/Nav.kt`
- Test: `src/test/kotlin/com/juiceplan/nav/NavTest.kt`

**Interfaces:**
- Consumes: 없음
- Produces:
  - `com.juiceplan.nav.NavTab(id: String, icon: String, label: String)`
  - `com.juiceplan.nav.NavSection(id: String, defaultTab: String, tabs: List<NavTab>)` — 프로퍼티 `path: String`, 메서드 `defaultPath(): String`, `has(tab: String): Boolean`
  - `com.juiceplan.nav.Nav` — `SECTIONS: List<NavSection>`, `section(id): NavSection`, `prevPath(id): String?`, `nextPath(id): String?`
  - `fun Model.addNav(sectionId: String, tab: String)` — 확장 함수, `section`/`activeTab`/`prevPath`/`nextPath` 를 한 번에 넣는다

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`src/test/kotlin/com/juiceplan/nav/NavTest.kt`:

```kotlin
package com.juiceplan.nav

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NavTest {

    @Test
    fun `sections are ordered schd, budget, check`() {
        assertEquals(listOf("schd", "budget", "check"), Nav.SECTIONS.map { it.id })
    }

    @Test
    fun `the first section has no previous and the last has no next`() {
        assertNull(Nav.prevPath("schd"))
        assertNull(Nav.nextPath("check"))
    }

    @Test
    fun `budget sits between schd and check`() {
        assertEquals("/schd", Nav.prevPath("budget"))
        assertEquals("/check", Nav.nextPath("budget"))
    }

    @Test
    fun `arrows point at the section root, not at a tab`() {
        // 기본 탭이 무엇인지는 서버 리다이렉트가 정한다. 링크에 탭을 박으면
        // 기본 탭을 바꿀 때 여기저기 고쳐야 한다.
        assertEquals("/schd", Nav.section("schd").path)
    }

    @Test
    fun `every section's default tab is one of its own tabs`() {
        Nav.SECTIONS.forEach {
            assertTrue(it.has(it.defaultTab), "${it.id} 의 기본 탭이 탭 목록에 없다")
        }
    }

    @Test
    fun `default path joins the section and its default tab`() {
        assertEquals("/schd/day", Nav.section("schd").defaultPath())
        assertEquals("/budget/summary", Nav.section("budget").defaultPath())
        assertEquals("/check/shopping", Nav.section("check").defaultPath())
    }

    @Test
    fun `schd keeps its display order add, plan, day`() {
        assertEquals(listOf("add", "plan", "day"), Nav.section("schd").tabs.map { it.id })
    }

    @Test
    fun `an unknown section id is a programming error`() {
        assertThrows(IllegalArgumentException::class.java) { Nav.section("nope") }
        assertThrows(IllegalArgumentException::class.java) { Nav.nextPath("nope") }
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew test --tests "com.juiceplan.nav.NavTest"`
Expected: FAIL — `Unresolved reference: Nav` 컴파일 에러

- [ ] **Step 3: 구현한다**

`src/main/kotlin/com/juiceplan/nav/Nav.kt`:

```kotlin
package com.juiceplan.nav

import org.springframework.ui.Model

/** 탭바에 한 칸으로 그려지는 탭. id 는 주소의 마지막 조각과 같다. */
data class NavTab(val id: String, val icon: String, val label: String)

/**
 * 화면 묶음 하나. 탭바 가운데에 자기 탭들을 그리고, 양 끝 화살표로 이웃 섹션과 오간다.
 *
 * defaultTab 을 따로 갖는 이유는 표시 순서와 기본 탭이 다를 수 있어서다. schd 는
 * add · plan · day 순으로 그리지만 처음 열리는 탭은 day 다.
 */
data class NavSection(val id: String, val defaultTab: String, val tabs: List<NavTab>) {
    val path: String get() = "/$id"

    fun defaultPath(): String = "$path/$defaultTab"

    fun has(tab: String): Boolean = tabs.any { it.id == tab }
}

/**
 * 섹션 목록은 여기 하나뿐이다. 순서가 곧 탭바 화살표의 순서다.
 *
 * 클라이언트(shell.js, budget.js)도 자기 섹션의 탭 목록을 갖고 있다. 중복이지만,
 * 그쪽은 지도를 재생성하지 않으려고 주소만 바꾸는 클라이언트 사정이라 서버 설정을
 * 내려받게 엮지 않는다.
 */
object Nav {
    val SECTIONS = listOf(
        NavSection(
            id = "schd", defaultTab = "day",
            tabs = listOf(
                NavTab("add", "📍", "장소 추가"),
                NavTab("plan", "🗓️", "동선 변경"),
                NavTab("day", "🧭", "계획 보기"),
            )
        ),
        NavSection(
            id = "budget", defaultTab = "summary",
            tabs = listOf(
                NavTab("summary", "📊", "예산 요약"),
                NavTab("list", "🧾", "지출 내역"),
            )
        ),
        NavSection(
            id = "check", defaultTab = "shopping",
            tabs = listOf(
                NavTab("shopping", "🛒", "쇼핑 목록"),
            )
        ),
    )

    fun section(id: String): NavSection =
        SECTIONS.firstOrNull { it.id == id } ?: throw IllegalArgumentException("알 수 없는 섹션: $id")

    /** 이웃이 없으면 null. 탭바는 그 자리에 눌리지 않는 화살표를 그린다. */
    fun prevPath(id: String): String? = neighbor(id, -1)

    fun nextPath(id: String): String? = neighbor(id, 1)

    private fun neighbor(id: String, step: Int): String? {
        val index = SECTIONS.indexOfFirst { it.id == id }
        require(index >= 0) { "알 수 없는 섹션: $id" }
        return SECTIONS.getOrNull(index + step)?.path
    }
}

/** 탭바 프래그먼트가 쓰는 값 네 개. 섹션 컨트롤러마다 같은 네 줄을 쓰지 않도록 묶었다. */
fun Model.addNav(sectionId: String, tab: String) {
    addAttribute("section", Nav.section(sectionId))
    addAttribute("activeTab", tab)
    addAttribute("prevPath", Nav.prevPath(sectionId))
    addAttribute("nextPath", Nav.nextPath(sectionId))
}
```

- [ ] **Step 4: 통과를 확인한다**

Run: `./gradlew test --tests "com.juiceplan.nav.NavTest"`
Expected: PASS (8개 테스트)

- [ ] **Step 5: 커밋**

```bash
git add src/main/kotlin/com/juiceplan/nav/Nav.kt src/test/kotlin/com/juiceplan/nav/NavTest.kt
git commit -m "feat: 섹션·탭 정의를 Nav 한 곳에 모은다"
```

---

## Task 2: 공용 탭바 + 세 섹션 라우트

셋 다 실제로 열리고 화살표로 오갈 수 있게 만든다. 예산 페이지는 이 태스크에서 빈 뷰 컨테이너와 탭 전환까지만 하고, 내용은 Task 6에서 채운다.

**Files:**
- Create: `src/main/resources/templates/fragments/tabbar.html`
- Create: `src/main/resources/templates/budget/index.html`
- Create: `src/main/resources/templates/check/index.html`
- Create: `src/main/kotlin/com/juiceplan/budget/BudgetPageController.kt`
- Create: `src/main/kotlin/com/juiceplan/check/CheckPageController.kt`
- Create: `src/main/resources/static/js/budget.js`
- Modify: `src/main/kotlin/com/juiceplan/shell/ShellController.kt`
- Modify: `src/main/resources/templates/shell/index.html:129-135`
- Modify: `src/main/resources/static/css/style.css`
- Delete: `src/main/resources/templates/fragments/layout.html`
- Test: `src/test/kotlin/com/juiceplan/nav/SectionNavigationIntegrationTest.kt`

**Interfaces:**
- Consumes: `Nav`, `NavSection`, `Model.addNav` (Task 1)
- Produces:
  - Thymeleaf 프래그먼트 `~{fragments/tabbar :: tabbar}` — `section`, `activeTab`, `prevPath`, `nextPath` 모델 값을 읽는다
  - 뷰 이름 `budget/index`, `check/index`
  - CSS 클래스 `.section-body` (지도 없는 섹션의 본문 상자), `.nav-arrow`, `.nav-arrow--off`
  - `budget/index.html` 안의 `#view-summary`, `#view-list` 섹션 엘리먼트

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`src/test/kotlin/com/juiceplan/nav/SectionNavigationIntegrationTest.kt`:

```kotlin
package com.juiceplan.nav

import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SectionNavigationIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `each section root redirects to its default tab`() {
        mapOf(
            "/schd" to "/schd/day",
            "/budget" to "/budget/summary",
            "/check" to "/check/shopping"
        ).forEach { (root, defaultPath) ->
            mockMvc.perform(get(root))
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl(defaultPath))
        }
    }

    @Test
    fun `an unknown tab falls back to the section default`() {
        mapOf(
            "/budget/nope" to "/budget/summary",
            "/check/nope" to "/check/shopping"
        ).forEach { (path, defaultPath) ->
            mockMvc.perform(get(path))
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl(defaultPath))
        }
    }

    @Test
    fun `the budget page ships both tab views so the client can swap them`() {
        mockMvc.perform(get("/budget/list"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("id=\"view-summary\"")))
            .andExpect(content().string(containsString("id=\"view-list\"")))
    }

    @Test
    fun `the tab bar links to the neighbouring sections`() {
        mockMvc.perform(get("/budget/summary"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("href=\"/schd\"")))
            .andExpect(content().string(containsString("href=\"/check\"")))
    }

    @Test
    fun `boundary sections render an arrow that is not a link`() {
        mockMvc.perform(get("/schd/day"))
            .andExpect(content().string(not(containsString("aria-label=\"이전 화면\""))))
            .andExpect(content().string(containsString("nav-arrow--off")))
        mockMvc.perform(get("/check/shopping"))
            .andExpect(content().string(not(containsString("aria-label=\"다음 화면\""))))
            .andExpect(content().string(containsString("nav-arrow--off")))
    }

    @Test
    fun `the shell keeps its three tab links after moving to the fragment`() {
        mockMvc.perform(get("/schd/day"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("data-tab=\"add\"")))
            .andExpect(content().string(containsString("data-tab=\"plan\"")))
            .andExpect(content().string(containsString("data-tab=\"day\"")))
    }

    @Test
    fun `only tabs carry data-tab, so the client router leaves the arrows alone`() {
        // shell.js·budget.js 는 footer nav a[data-tab] 의 클릭만 가로챈다. 화살표가 그
        // 선택자에 걸리면 섹션 이동이 조용히 죽는다. 예산 섹션의 탭은 둘뿐이다.
        val html = mockMvc.perform(get("/budget/summary")).andReturn().response.contentAsString

        assertEquals(2, Regex("data-tab=").findAll(html).count())
    }
}
```

`assertEquals` 임포트를 함께 넣는다: `import org.junit.jupiter.api.Assertions.assertEquals`

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew test --tests "com.juiceplan.nav.SectionNavigationIntegrationTest"`
Expected: FAIL — `/budget`, `/check` 가 404

- [ ] **Step 3: 탭바 프래그먼트를 만든다**

`src/main/resources/templates/fragments/tabbar.html`:

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org">
<body>
<!--/* 세 섹션이 함께 쓰는 하단 탭바. 가운데는 지금 섹션의 탭, 양 끝은 이웃 섹션으로 가는 화살표다.

       화살표에는 data-tab 이 없다. shell.js·budget.js 가 클릭을 가로채는 선택자가
       footer nav a[data-tab] 이라, 화살표가 거기 걸리면 페이지를 옮기지 못하고 죽는다.

       이웃이 없는 끝 섹션은 <a> 대신 <span> 을 그린다. 자리는 남겨야 탭 위치가
       섹션마다 좌우로 흔들리지 않는다. */-->
<footer th:fragment="tabbar">
    <nav>
        <a class="nav-arrow" th:if="${prevPath}" th:href="@{${prevPath}}" aria-label="이전 화면">‹</a>
        <span class="nav-arrow nav-arrow--off" th:unless="${prevPath}" aria-hidden="true">‹</span>

        <a th:each="t : ${section.tabs}"
           th:href="@{'/' + ${section.id} + '/' + ${t.id}}"
           th:attr="data-tab=${t.id}"
           th:classappend="${t.id == activeTab} ? 'active' : ''">
            <span aria-hidden="true" th:text="${t.icon}"></span><span th:text="${t.label}"></span>
        </a>

        <a class="nav-arrow" th:if="${nextPath}" th:href="@{${nextPath}}" aria-label="다음 화면">›</a>
        <span class="nav-arrow nav-arrow--off" th:unless="${nextPath}" aria-hidden="true">›</span>
    </nav>
</footer>
</body>
</html>
```

- [ ] **Step 4: 셸을 프래그먼트에 연결한다**

`src/main/resources/templates/shell/index.html` 의 `<footer>` 블록(129~135줄)을 통째로 바꾼다:

```html
<footer th:replace="~{fragments/tabbar :: tabbar}"></footer>
```

`src/main/kotlin/com/juiceplan/shell/ShellController.kt` 를 아래 내용으로 바꾼다:

```kotlin
package com.juiceplan.shell

import com.juiceplan.daynote.DayNoteService
import com.juiceplan.nav.Nav
import com.juiceplan.nav.addNav
import com.juiceplan.source.SourceService
import com.juiceplan.trip.TripService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.servlet.view.RedirectView
import java.time.LocalDate

private const val SECTION = "schd"

/**
 * 앱의 일정 섹션은 이 셸 하나다. `/schd/add`, `/schd/plan`, `/schd/day` 셋 다 같은 셸을
 * 돌려주고 어느 뷰로 열지만 다르다.
 *
 * 탭을 옮길 때는 클라이언트가 history.pushState 로 주소만 바꾸므로 여기까지 오지 않는다.
 * 페이지를 다시 그리면 구글맵이 통째로 재생성되기 때문이다. 이 경로들은 새로고침과
 * 북마크, 공유 링크, 그리고 옆 섹션에서 화살표로 들어올 때만 쓰인다.
 */
@Controller
class ShellController(
    private val sourceService: SourceService,
    private val tripService: TripService,
    private val dayNoteService: DayNoteService
) {
    @GetMapping("/schd/{tab}")
    fun shell(@PathVariable tab: String, model: Model): Any {
        val section = Nav.section(SECTION)
        if (!section.has(tab)) return RedirectView(section.defaultPath())

        val trip = tripService.current()
        model.addAttribute("trip", trip)
        model.addAttribute("sources", sourceService.list())
        model.addAttribute(
            "dayNotes",
            if (trip == null) emptyMap<LocalDate, String>()
            else dayNoteService.allForRange(trip.startDate, trip.endDate)
        )
        model.addNav(SECTION, tab)
        return "shell/index"
    }

    @GetMapping("/", "/schd")
    fun root(): RedirectView = RedirectView(Nav.section(SECTION).defaultPath())

    /** /schd 를 앞에 붙이기 전, 그리고 단일 셸로 합치기 전의 북마크를 살린다. */
    @GetMapping("/sources", "/add")
    fun legacyAdd(): RedirectView = RedirectView("/schd/add")

    @GetMapping("/plan")
    fun legacyPlan(): RedirectView = RedirectView("/schd/plan")

    @GetMapping("/day")
    fun legacyDay(): RedirectView = RedirectView("/schd/day")
}
```

- [ ] **Step 5: 예산·체크리스트 페이지 컨트롤러를 만든다**

`src/main/kotlin/com/juiceplan/budget/BudgetPageController.kt`:

```kotlin
package com.juiceplan.budget

import com.juiceplan.nav.Nav
import com.juiceplan.nav.addNav
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.servlet.view.RedirectView

private const val SECTION = "budget"

/**
 * 예산 페이지. 두 탭이 같은 페이지를 쓰고 클라이언트가 갈아끼운다. 지도가 없으므로
 * 셸처럼 조심할 건 없지만, 탭을 옮길 때 서버로 다시 갈 이유도 없다.
 */
@Controller
class BudgetPageController {

    @GetMapping("/budget")
    fun root(): RedirectView = RedirectView(Nav.section(SECTION).defaultPath())

    @GetMapping("/budget/{tab}")
    fun page(@PathVariable tab: String, model: Model): Any {
        val section = Nav.section(SECTION)
        if (!section.has(tab)) return RedirectView(section.defaultPath())

        model.addNav(SECTION, tab)
        return "budget/index"
    }
}
```

`src/main/kotlin/com/juiceplan/check/CheckPageController.kt`:

```kotlin
package com.juiceplan.check

import com.juiceplan.nav.Nav
import com.juiceplan.nav.addNav
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.servlet.view.RedirectView

private const val SECTION = "check"

/** 아직 내용이 없다. 화살표 이동과 리다이렉트 구조를 먼저 완성해 두려고 자리만 잡는다. */
@Controller
class CheckPageController {

    @GetMapping("/check")
    fun root(): RedirectView = RedirectView(Nav.section(SECTION).defaultPath())

    @GetMapping("/check/{tab}")
    fun page(@PathVariable tab: String, model: Model): Any {
        val section = Nav.section(SECTION)
        if (!section.has(tab)) return RedirectView(section.defaultPath())

        model.addNav(SECTION, tab)
        return "check/index"
    }
}
```

- [ ] **Step 6: 두 페이지 템플릿을 만든다**

`src/main/resources/templates/budget/index.html`:

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <!-- viewport-fit=cover 가 있어야 env(safe-area-inset-*) 가 동작한다 -->
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <title>juice-plan</title>
    <link rel="stylesheet" th:href="@{/css/style.css}">
</head>
<body>

<div class="section-body">
    <section id="view-summary" class="view" hidden></section>
    <section id="view-list" class="view" hidden></section>
</div>

<footer th:replace="~{fragments/tabbar :: tabbar}"></footer>

<script th:src="@{/js/budget.js}"></script>
</body>
</html>
```

`src/main/resources/templates/check/index.html`:

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <title>juice-plan</title>
    <link rel="stylesheet" th:href="@{/css/style.css}">
</head>
<body>

<div class="section-body">
    <section class="view">
        <p class="card muted">쇼핑 목록은 아직 준비 중입니다.</p>
    </section>
</div>

<footer th:replace="~{fragments/tabbar :: tabbar}"></footer>
</body>
</html>
```

- [ ] **Step 7: 예산 섹션 라우팅 JS를 만든다**

`src/main/resources/static/js/budget.js`:

```js
// 예산 섹션 라우팅. shell.js 와 같은 방식으로 탭을 옮길 때 서버로 가지 않고 주소만 바꾼다.
// 여기엔 지도가 없어 지킬 게 없지만, 탭을 누를 때마다 페이지를 새로 받을 이유도 없다.
(function () {
    const TABS = ['summary', 'list'];
    const DEFAULT_TAB = 'summary';
    const BASE = '/budget';   // BudgetPageController 의 @GetMapping("/budget/{tab}") 과 같아야 한다

    let current = null;

    function tabFromPath() {
        const last = window.location.pathname.replace(/\/+$/, '').split('/').pop();
        return TABS.includes(last) ? last : DEFAULT_TAB;
    }

    function pathOf(tab) {
        return BASE + '/' + tab;
    }

    function show(tab) {
        TABS.forEach((t) => {
            document.getElementById('view-' + t).hidden = t !== tab;
        });
        document.querySelectorAll('footer nav a[data-tab]').forEach((a) => {
            a.classList.toggle('active', a.dataset.tab === tab);
        });
        current = tab;
    }

    function route() {
        const tab = tabFromPath();
        show(tab);
        if (window.location.pathname !== pathOf(tab)) {
            history.replaceState({}, '', pathOf(tab));
        }
    }

    function navigate(tab) {
        if (window.location.pathname === pathOf(tab)) return;
        history.pushState({}, '', pathOf(tab));
        route();
    }

    window.addEventListener('popstate', route);

    // 링크는 진짜 주소를 그대로 두고(새 탭·복사가 되도록) 클릭만 가로챈다.
    // 화살표에는 data-tab 이 없어 여기 걸리지 않고 페이지를 옮긴다.
    document.querySelectorAll('footer nav a[data-tab]').forEach((a) => {
        a.addEventListener('click', (e) => {
            e.preventDefault();
            navigate(a.dataset.tab);
        });
    });

    route();
})();
```

- [ ] **Step 8: CSS를 더한다**

`style.css` 의 `/* ---- 하단 탭바 ---- */` 블록 안, `footer nav a.active` 규칙 바로 아래에 붙인다:

```css
/* 양 끝 화살표는 이웃 섹션으로 간다. footer nav a 의 flex:1 을 그대로 두면
   화살표가 탭만큼 넓어져 탭 이름이 눌린다. */
.nav-arrow {
  flex: 0 0 32px;
  display: flex; align-items: center; justify-content: center;
  min-height: var(--tabbar-h);
  color: var(--muted); font-size: 22px; line-height: 1;
  text-decoration: none;
}
/* 끝 섹션에는 갈 곳이 없다. <span> 이라 눌리지 않지만 자리는 남겨야
   탭 위치가 섹션을 옮길 때마다 좌우로 흔들리지 않는다. */
.nav-arrow--off { opacity: .25; }

/* ---- 지도가 없는 섹션(예산·체크리스트)의 본문 ---- */
/* 셸은 지도 위에 시트를 덮지만 여기는 덮을 게 없다. 탭바 높이만 남기고 화면을 채운다. */
.section-body {
  position: fixed; inset: 0;
  bottom: calc(var(--tabbar-h) + env(safe-area-inset-bottom));
  display: flex; flex-direction: column;
}
```

- [ ] **Step 9: 죽은 프래그먼트를 지운다**

```bash
git rm src/main/resources/templates/fragments/layout.html
```

지우기 전에 아무 데서도 안 쓰는지 확인한다:

Run: `grep -rn "layout" src/main/resources/templates/ src/main/kotlin/`
Expected: 아무 결과 없음

- [ ] **Step 10: 테스트 통과를 확인한다**

Run: `./gradlew test`
Expected: PASS — 새 `SectionNavigationIntegrationTest` 7개와 기존 `ShellControllerIntegrationTest` 전부

- [ ] **Step 11: 눈으로 확인한다**

Run: `./gradlew bootRun`

브라우저(모바일 폭)에서 `http://localhost:8080/` 을 연다.

1. `/schd/day` 로 리다이렉트되고 탭바 왼쪽 `‹` 가 흐리게 죽어 있다
2. 오른쪽 `›` 를 누르면 `/budget/summary` 로 간다 (화면은 아직 비어 있다)
3. 탭바에 `예산 요약`, `지출 내역` 두 탭이 보이고, 누르면 주소가 바뀌며 활성 표시가 옮겨간다 (새로고침 없이)
4. `›` 를 다시 누르면 `/check/shopping`, "준비 중입니다" 카드가 보이고 `›` 가 죽어 있다
5. `‹` 를 두 번 눌러 `/schd/day` 로 돌아온다

- [ ] **Step 12: 커밋**

```bash
git add -A
git commit -m "feat: 섹션 사이를 오가는 공용 하단 탭바"
```

---

## Task 3: 지출 항목 모델과 합계 계산

**Files:**
- Create: `src/main/kotlin/com/juiceplan/budget/BudgetItem.kt`
- Create: `src/main/kotlin/com/juiceplan/budget/BudgetSetting.kt`
- Create: `src/main/kotlin/com/juiceplan/budget/BudgetRepositories.kt`
- Create: `src/main/kotlin/com/juiceplan/budget/BudgetTotals.kt`
- Test: `src/test/kotlin/com/juiceplan/budget/BudgetTotalsTest.kt`

**Interfaces:**
- Consumes: 없음
- Produces:
  - `BudgetCategory { FLIGHT, HOTEL, FOOD, TRANSIT, ACTIVITY, SHOPPING, ETC }` (선언 순서 = 표 행 순서)
  - `PaymentMethod { CREDIT_CARD, TRAVEL_LOG, CASH }`, `SettlementStatus { PENDING, DONE, NOT_APPLICABLE }`, `Currency { JPY, KRW }`
  - `BudgetItem(id, name, category, paymentMethod, currency, amount, settlement, memo)`
  - `BudgetSetting(id, ratePer100Jpy)`, 상수 `SETTING_ID = 1L`
  - `BudgetItemRepository : JpaRepository<BudgetItem, Long>`, `BudgetSettingRepository : JpaRepository<BudgetSetting, Long>`
  - `BudgetTotals.summarize(items: List<BudgetItem>, ratePer100Jpy: Int): BudgetSummary`
  - `BudgetTotals.jpyToKrw(jpy: Int, ratePer100Jpy: Int): Int`
  - `Money(jpy, krw)`, `CategoryTotal(category, count, currencies, total, perPerson, convertedKrw)`, `BudgetSummary(rows, count, currencies, total, perPerson, ratePer100Jpy, convertedTotalKrw, convertedPerPersonKrw)`
  - 상수 `HEADCOUNT = 2`

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`src/test/kotlin/com/juiceplan/budget/BudgetTotalsTest.kt`:

```kotlin
package com.juiceplan.budget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val RATE = 900   // 100엔 = ₩900

class BudgetTotalsTest {

    private fun item(
        category: BudgetCategory,
        currency: Currency,
        amount: Int
    ) = BudgetItem(
        name = "",
        category = category,
        paymentMethod = PaymentMethod.TRAVEL_LOG,
        currency = currency,
        amount = amount,
        settlement = SettlementStatus.PENDING
    )

    /**
     * 실제로 쓰는 21개 항목의 카테고리·통화·금액만 뽑았다. 스펙 5절 요약표의 숫자가
     * 이 데이터에서 그대로 나와야 한다.
     */
    private val realBudget: List<BudgetItem> =
        listOf(
            item(BudgetCategory.FLIGHT, Currency.KRW, 853_800),
            item(BudgetCategory.HOTEL, Currency.KRW, 0),
            item(BudgetCategory.TRANSIT, Currency.JPY, 5_440),
            item(BudgetCategory.TRANSIT, Currency.JPY, 3_000),
            item(BudgetCategory.TRANSIT, Currency.JPY, 1_040),
            item(BudgetCategory.TRANSIT, Currency.JPY, 4_000),
            item(BudgetCategory.ACTIVITY, Currency.JPY, 3_600),
            item(BudgetCategory.SHOPPING, Currency.JPY, 0),
            item(BudgetCategory.SHOPPING, Currency.JPY, 0),
            item(BudgetCategory.ETC, Currency.KRW, 0),
            item(BudgetCategory.ETC, Currency.KRW, 0),
        ) + List(10) { item(BudgetCategory.FOOD, Currency.JPY, 0) }

    private fun row(summary: BudgetSummary, category: BudgetCategory) =
        summary.rows.first { it.category == category }

    @Test
    fun `the real budget adds up to 21 items`() {
        assertEquals(21, BudgetTotals.summarize(realBudget, RATE).count)
    }

    @Test
    fun `each category stays in the currency it was paid in`() {
        val summary = BudgetTotals.summarize(realBudget, RATE)

        assertEquals(Money(jpy = 0, krw = 853_800), row(summary, BudgetCategory.FLIGHT).total)
        assertEquals(Money(jpy = 13_480, krw = 0), row(summary, BudgetCategory.TRANSIT).total)
        assertEquals(Money(jpy = 3_600, krw = 0), row(summary, BudgetCategory.ACTIVITY).total)
    }

    @Test
    fun `each category reports which currencies were actually used`() {
        // 금액이 전부 0이면 합계만 봐서는 ¥0 인지 ₩0 인지 알 수 없다
        val summary = BudgetTotals.summarize(realBudget, RATE)

        assertEquals(listOf(Currency.JPY), row(summary, BudgetCategory.FOOD).currencies)
        assertEquals(listOf(Currency.KRW), row(summary, BudgetCategory.HOTEL).currencies)
    }

    @Test
    fun `a category with both currencies reports both`() {
        val mixed = listOf(
            item(BudgetCategory.FOOD, Currency.JPY, 3_000),
            item(BudgetCategory.FOOD, Currency.KRW, 20_000),
        )

        val row = BudgetTotals.summarize(mixed, RATE).rows.single()

        assertEquals(listOf(Currency.JPY, Currency.KRW), row.currencies)
        assertEquals(Money(jpy = 3_000, krw = 20_000), row.total)
    }

    @Test
    fun `per person halves the two-person total and drops the remainder`() {
        val odd = listOf(item(BudgetCategory.TRANSIT, Currency.JPY, 1_041))

        assertEquals(Money(jpy = 520, krw = 0), BudgetTotals.summarize(odd, RATE).perPerson)
    }

    @Test
    fun `the grand total keeps the two currencies apart`() {
        val summary = BudgetTotals.summarize(realBudget, RATE)

        assertEquals(Money(jpy = 17_080, krw = 853_800), summary.total)
        assertEquals(Money(jpy = 8_540, krw = 426_900), summary.perPerson)
    }

    @Test
    fun `the converted total applies the rate to the yen side only`() {
        val summary = BudgetTotals.summarize(realBudget, RATE)

        assertEquals(1_007_520, summary.convertedTotalKrw)
        assertEquals(503_760, summary.convertedPerPersonKrw)
    }

    @Test
    fun `changing the rate moves only the yen side`() {
        val summary = BudgetTotals.summarize(realBudget, 1000)

        assertEquals(Money(jpy = 17_080, krw = 853_800), summary.total)
        assertEquals(853_800 + 170_800, summary.convertedTotalKrw)
    }

    @Test
    fun `each category carries its own converted value for the chart`() {
        val summary = BudgetTotals.summarize(realBudget, RATE)

        assertEquals(853_800, row(summary, BudgetCategory.FLIGHT).convertedKrw)
        assertEquals(121_320, row(summary, BudgetCategory.TRANSIT).convertedKrw)
        assertEquals(32_400, row(summary, BudgetCategory.ACTIVITY).convertedKrw)
    }

    @Test
    fun `converting rounds half up`() {
        // 1엔 × 900/100 = 9원, 5엔 × 950/100 = 47.5 → 48
        assertEquals(9, BudgetTotals.jpyToKrw(1, 900))
        assertEquals(48, BudgetTotals.jpyToKrw(5, 950))
    }

    @Test
    fun `rows follow the declared category order`() {
        val summary = BudgetTotals.summarize(realBudget, RATE)

        assertEquals(BudgetCategory.values().toList(), summary.rows.map { it.category })
    }

    @Test
    fun `categories with no items are left out`() {
        val onlyFood = listOf(item(BudgetCategory.FOOD, Currency.JPY, 1_000))

        assertEquals(listOf(BudgetCategory.FOOD), BudgetTotals.summarize(onlyFood, RATE).rows.map { it.category })
    }

    @Test
    fun `an empty budget has no rows and a zero total`() {
        val summary = BudgetTotals.summarize(emptyList(), RATE)

        assertTrue(summary.rows.isEmpty())
        assertEquals(0, summary.count)
        assertEquals(Money(0, 0), summary.total)
        assertEquals(0, summary.convertedTotalKrw)
        assertTrue(summary.currencies.isEmpty())
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew test --tests "com.juiceplan.budget.BudgetTotalsTest"`
Expected: FAIL — `Unresolved reference: BudgetItem`

- [ ] **Step 3: 엔티티와 리포지토리를 만든다**

`src/main/kotlin/com/juiceplan/budget/BudgetItem.kt`:

```kotlin
package com.juiceplan.budget

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/** 지출 분류. 요약표의 행 순서가 이 선언 순서다. */
enum class BudgetCategory { FLIGHT, HOTEL, FOOD, TRANSIT, ACTIVITY, SHOPPING, ETC }

enum class PaymentMethod { CREDIT_CARD, TRAVEL_LOG, CASH }

enum class SettlementStatus { PENDING, DONE, NOT_APPLICABLE }

/** 결제한 통화. 항목은 이 통화로만 기입하고 환산값은 저장하지 않는다. */
enum class Currency { JPY, KRW }

/**
 * 지출 항목 하나. 금액은 늘 2인 총액이고 통화는 실제로 결제한 쪽 하나뿐이다.
 *
 * 모든 enum 이 문자열로 저장된다. Hibernate 는 H2 에서 enum 필드를 ENUM('A','B') 네이티브
 * 컬럼으로 만드는데, ddl-auto: update 가 이미 있는 ENUM 의 값 목록을 넓혀주지 않아
 * 종류를 하나 더하면 기존 DB 가 새 값을 거부한다. Source.placeType 과 같은 이유다.
 */
@Entity
class BudgetItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    // 이름 없이 카테고리와 결제 수단만 잡아둔 자리(식비 10건)가 있으므로 빈 문자열을 허용한다
    var name: String,

    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    var category: BudgetCategory,

    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    var paymentMethod: PaymentMethod,

    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    var currency: Currency,

    /** 2인 총액, 결제 통화 기준. 아직 안 정한 항목은 0이다. */
    var amount: Int,

    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    var settlement: SettlementStatus,

    var memo: String? = null
)
```

`src/main/kotlin/com/juiceplan/budget/BudgetSetting.kt`:

```kotlin
package com.juiceplan.budget

import jakarta.persistence.Entity
import jakarta.persistence.Id

/** 설정은 한 행뿐이라 id 를 고정한다. */
const val SETTING_ID = 1L

/** 기본 환율. 100엔 = ₩900. */
const val DEFAULT_RATE_PER_100_JPY = 900

/**
 * 예산 화면의 설정. 지금은 환율 하나뿐이다.
 *
 * 단위는 화면·API·DB 전부 "100엔당 원"인 정수다. 사람이 환율을 말할 때 쓰는 단위를
 * 그대로 저장하므로 어디서도 단위를 바꿔 담을 일이 없다.
 *
 * 테이블 이름은 BUDGET_SETTING 이다. 예전 인증이 쓰던 APP_SETTINGS 는
 * SchemaMigration.dropAppSettings() 가 매 부팅마다 지우므로 그 이름을 재사용하면 안 된다.
 */
@Entity
class BudgetSetting(
    @Id
    val id: Long = SETTING_ID,

    var ratePer100Jpy: Int = DEFAULT_RATE_PER_100_JPY
)
```

`src/main/kotlin/com/juiceplan/budget/BudgetRepositories.kt`:

```kotlin
package com.juiceplan.budget

import org.springframework.data.jpa.repository.JpaRepository

interface BudgetItemRepository : JpaRepository<BudgetItem, Long>

interface BudgetSettingRepository : JpaRepository<BudgetSetting, Long>
```

- [ ] **Step 4: 합계 계산을 만든다**

`src/main/kotlin/com/juiceplan/budget/BudgetTotals.kt`:

```kotlin
package com.juiceplan.budget

import java.math.BigDecimal
import java.math.RoundingMode

/** 예산은 늘 2인 기준이다. 인원 설정은 만들지 않는다. */
const val HEADCOUNT = 2

/** 통화별 2인 총액. 한 카테고리에 엔·원이 섞이면 둘 다 0이 아니다. */
data class Money(val jpy: Int = 0, val krw: Int = 0) {
    operator fun plus(other: Money) = Money(jpy + other.jpy, krw + other.krw)

    /** 나머지는 버린다. 1원을 맞추자고 한 사람에게 몰아주지 않는다. */
    fun perPerson() = Money(jpy / HEADCOUNT, krw / HEADCOUNT)
}

data class CategoryTotal(
    val category: BudgetCategory,
    val count: Int,
    /** 이 카테고리에 실제로 쓰인 결제 통화. 금액이 전부 0일 때 ¥0 인지 ₩0 인지는 여기서만 안다. */
    val currencies: List<Currency>,
    val total: Money,
    val perPerson: Money,
    /** 차트 비중용. 표에는 쓰지 않는다. */
    val convertedKrw: Int
)

data class BudgetSummary(
    val rows: List<CategoryTotal>,
    val count: Int,
    val currencies: List<Currency>,
    val total: Money,
    val perPerson: Money,
    val ratePer100Jpy: Int,
    val convertedTotalKrw: Int,
    val convertedPerPersonKrw: Int
)

/**
 * 합계 규칙만 모았다. DB 도 스프링도 모른다.
 *
 * 항목은 결제한 통화 그대로 더한다. 엔화를 원화로 바꾸는 건 카테고리 합계와 총액을 낼 때
 * 한 번씩뿐이다. 항목마다 환산해서 더하면 반올림이 항목 수만큼 쌓여 카테고리 합계와
 * 총액이 몇 원씩 어긋난다.
 */
object BudgetTotals {

    fun jpyToKrw(jpy: Int, ratePer100Jpy: Int): Int =
        BigDecimal(jpy)
            .multiply(BigDecimal(ratePer100Jpy))
            .divide(BigDecimal(100))
            .setScale(0, RoundingMode.HALF_UP)
            .toInt()

    fun summarize(items: List<BudgetItem>, ratePer100Jpy: Int): BudgetSummary {
        val rows = BudgetCategory.values()
            .map { category -> category to items.filter { it.category == category } }
            .filter { (_, inCategory) -> inCategory.isNotEmpty() }
            .map { (category, inCategory) -> categoryTotal(category, inCategory, ratePer100Jpy) }

        val total = rows.fold(Money()) { acc, row -> acc + row.total }
        val converted = convert(total, ratePer100Jpy)

        return BudgetSummary(
            rows = rows,
            count = items.size,
            currencies = currenciesOf(items),
            total = total,
            perPerson = total.perPerson(),
            ratePer100Jpy = ratePer100Jpy,
            convertedTotalKrw = converted,
            convertedPerPersonKrw = converted / HEADCOUNT
        )
    }

    private fun categoryTotal(
        category: BudgetCategory,
        items: List<BudgetItem>,
        ratePer100Jpy: Int
    ): CategoryTotal {
        val total = items.fold(Money()) { acc, item -> acc + moneyOf(item) }
        return CategoryTotal(
            category = category,
            count = items.size,
            currencies = currenciesOf(items),
            total = total,
            perPerson = total.perPerson(),
            convertedKrw = convert(total, ratePer100Jpy)
        )
    }

    private fun moneyOf(item: BudgetItem) = when (item.currency) {
        Currency.JPY -> Money(jpy = item.amount)
        Currency.KRW -> Money(krw = item.amount)
    }

    /** 선언 순서(JPY, KRW)로 돌려준다. 화면이 두 줄을 늘 같은 순서로 그리게 하기 위해서다. */
    private fun currenciesOf(items: List<BudgetItem>): List<Currency> =
        Currency.values().filter { currency -> items.any { it.currency == currency } }

    private fun convert(money: Money, ratePer100Jpy: Int) =
        money.krw + jpyToKrw(money.jpy, ratePer100Jpy)
}
```

- [ ] **Step 5: 통과를 확인한다**

Run: `./gradlew test --tests "com.juiceplan.budget.BudgetTotalsTest"`
Expected: PASS (13개 테스트)

- [ ] **Step 6: 커밋**

```bash
git add src/main/kotlin/com/juiceplan/budget src/test/kotlin/com/juiceplan/budget
git commit -m "feat: 지출 항목 모델과 통화별 합계 계산"
```

---

## Task 4: 예산 서비스와 API

**Files:**
- Create: `src/main/kotlin/com/juiceplan/budget/BudgetService.kt`
- Create: `src/main/kotlin/com/juiceplan/budget/BudgetApiController.kt`
- Test: `src/test/kotlin/com/juiceplan/budget/BudgetServiceTest.kt`
- Test: `src/test/kotlin/com/juiceplan/budget/BudgetApiIntegrationTest.kt`

**Interfaces:**
- Consumes: Task 3 전부
- Produces:
  - `BudgetItemInput(name, category, paymentMethod, currency, amount, settlement, memo)`
  - `BudgetService.list(): List<BudgetItem>` — 카테고리 선언 순 → id 순
  - `BudgetService.summary(): BudgetSummary`
  - `BudgetService.create(input): BudgetItem`, `update(id, input): BudgetItem`, `delete(id)`
  - `BudgetService.rate(): Int`, `BudgetService.saveRate(ratePer100Jpy: Int)`
  - HTTP: `GET /api/budget/summary`, `POST|PUT|DELETE /api/budget/items[/{id}]`, `PUT /api/budget/rate`

- [ ] **Step 1: 서비스 테스트를 쓴다**

`src/test/kotlin/com/juiceplan/budget/BudgetServiceTest.kt`:

```kotlin
package com.juiceplan.budget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import

@DataJpaTest
@Import(BudgetService::class)
class BudgetServiceTest {

    @Autowired lateinit var budgetService: BudgetService
    @Autowired lateinit var itemRepository: BudgetItemRepository
    @Autowired lateinit var settingRepository: BudgetSettingRepository

    // 테스트용 H2 는 DB_CLOSE_DELAY=-1 이라 이 JVM 의 모든 스프링 컨텍스트가 같은 DB 를 쓴다.
    // @SpringBootTest 가 커밋한 설정 행이 남아 있으면 기본 환율 테스트가 그 값을 본다.
    @BeforeEach
    fun clearBudget() {
        itemRepository.deleteAll()
        settingRepository.deleteAll()
    }

    private fun input(
        name: String = "테스트 지출",
        category: BudgetCategory = BudgetCategory.TRANSIT,
        currency: Currency = Currency.JPY,
        amount: Int = 1_000,
        settlement: SettlementStatus = SettlementStatus.PENDING,
        memo: String? = null
    ) = BudgetItemInput(
        name = name,
        category = category,
        paymentMethod = PaymentMethod.TRAVEL_LOG,
        currency = currency,
        amount = amount,
        settlement = settlement,
        memo = memo
    )

    @Test
    fun `create stores the item and returns it with an id`() {
        val saved = budgetService.create(input(name = "오타루 JR"))

        assertTrue(saved.id > 0)
        assertEquals("오타루 JR", itemRepository.findById(saved.id).get().name)
    }

    @Test
    fun `create trims the name but keeps an empty one`() {
        assertEquals("돈키호테", budgetService.create(input(name = "  돈키호테  ")).name)
        assertEquals("", budgetService.create(input(name = "   ")).name)
    }

    @Test
    fun `create rejects a negative amount`() {
        assertThrows(IllegalArgumentException::class.java) { budgetService.create(input(amount = -1)) }
    }

    @Test
    fun `update overwrites every field`() {
        val saved = budgetService.create(input())

        val updated = budgetService.update(
            saved.id,
            input(name = "바뀐 이름", category = BudgetCategory.FOOD, currency = Currency.KRW, amount = 25_000, settlement = SettlementStatus.DONE, memo = "회식")
        )

        assertEquals("바뀐 이름", updated.name)
        assertEquals(BudgetCategory.FOOD, updated.category)
        assertEquals(Currency.KRW, updated.currency)
        assertEquals(25_000, updated.amount)
        assertEquals(SettlementStatus.DONE, updated.settlement)
        assertEquals("회식", updated.memo)
    }

    @Test
    fun `update and delete reject an unknown id`() {
        assertThrows(NoSuchElementException::class.java) { budgetService.update(999, input()) }
        assertThrows(NoSuchElementException::class.java) { budgetService.delete(999) }
    }

    @Test
    fun `delete removes the item`() {
        val saved = budgetService.create(input())

        budgetService.delete(saved.id)

        assertTrue(itemRepository.findById(saved.id).isEmpty)
    }

    @Test
    fun `list orders by declared category first, then by id`() {
        val food = budgetService.create(input(category = BudgetCategory.FOOD))
        val flight = budgetService.create(input(category = BudgetCategory.FLIGHT))
        val food2 = budgetService.create(input(category = BudgetCategory.FOOD))

        assertEquals(listOf(flight.id, food.id, food2.id), budgetService.list().map { it.id })
    }

    @Test
    fun `the rate defaults to 900 before anyone sets it`() {
        assertEquals(900, budgetService.rate())
    }

    @Test
    fun `saving the rate changes the converted total but not the stored amount`() {
        budgetService.create(input(currency = Currency.JPY, amount = 1_000))

        budgetService.saveRate(1_000)

        val summary = budgetService.summary()
        assertEquals(1_000, summary.total.jpy)
        assertEquals(10_000, summary.convertedTotalKrw)
        assertEquals(1_000, summary.ratePer100Jpy)
    }

    @Test
    fun `the rate must be positive`() {
        assertThrows(IllegalArgumentException::class.java) { budgetService.saveRate(0) }
        assertThrows(IllegalArgumentException::class.java) { budgetService.saveRate(-900) }
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew test --tests "com.juiceplan.budget.BudgetServiceTest"`
Expected: FAIL — `Unresolved reference: BudgetService`

- [ ] **Step 3: 서비스를 만든다**

`src/main/kotlin/com/juiceplan/budget/BudgetService.kt`:

```kotlin
package com.juiceplan.budget

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 화면이 보내는 항목 값. 엔티티와 달리 id 가 없고 이름에 앞뒤 공백이 남아 있다. */
data class BudgetItemInput(
    val name: String,
    val category: BudgetCategory,
    val paymentMethod: PaymentMethod,
    val currency: Currency,
    val amount: Int,
    val settlement: SettlementStatus,
    val memo: String? = null
)

@Service
class BudgetService(
    private val itemRepository: BudgetItemRepository,
    private val settingRepository: BudgetSettingRepository
) {
    /**
     * 카테고리 선언 순 → id 순. 화면이 카테고리별로 묶어 그리므로 이 순서가 곧 화면 순서다.
     * 정렬 컬럼을 따로 두지 않는 이유는 사람이 순서를 손으로 바꿀 일이 없어서다.
     */
    fun list(): List<BudgetItem> =
        itemRepository.findAll().sortedWith(compareBy({ it.category.ordinal }, { it.id }))

    fun summary(): BudgetSummary = BudgetTotals.summarize(list(), rate())

    fun rate(): Int = setting().ratePer100Jpy

    @Transactional
    fun saveRate(ratePer100Jpy: Int) {
        require(ratePer100Jpy > 0) { "환율은 0보다 커야 합니다." }
        settingRepository.save(setting().also { it.ratePer100Jpy = ratePer100Jpy })
    }

    @Transactional
    fun create(input: BudgetItemInput): BudgetItem {
        val clean = input.validated()
        return itemRepository.save(
            BudgetItem(
                name = clean.name,
                category = clean.category,
                paymentMethod = clean.paymentMethod,
                currency = clean.currency,
                amount = clean.amount,
                settlement = clean.settlement,
                memo = clean.memo
            )
        )
    }

    @Transactional
    fun update(id: Long, input: BudgetItemInput): BudgetItem {
        val clean = input.validated()
        val item = find(id)
        item.name = clean.name
        item.category = clean.category
        item.paymentMethod = clean.paymentMethod
        item.currency = clean.currency
        item.amount = clean.amount
        item.settlement = clean.settlement
        item.memo = clean.memo
        return itemRepository.save(item)
    }

    @Transactional
    fun delete(id: Long) {
        itemRepository.delete(find(id))
    }

    private fun find(id: Long): BudgetItem =
        itemRepository.findById(id).orElseThrow { NoSuchElementException("없는 지출 항목입니다.") }

    /** 설정 행은 처음 읽을 때 기본값으로 만들어진다. 시드가 돌기 전에 열어도 화면이 비지 않는다. */
    private fun setting(): BudgetSetting =
        settingRepository.findById(SETTING_ID).orElseGet { settingRepository.save(BudgetSetting()) }
}

/** 이름은 앞뒤 공백만 잘라내고 비어 있어도 통과시킨다. 금액은 음수만 막는다. */
private fun BudgetItemInput.validated(): BudgetItemInput {
    require(amount >= 0) { "금액은 0보다 작을 수 없습니다." }
    return copy(name = name.trim(), memo = memo?.trim()?.ifEmpty { null })
}
```

- [ ] **Step 4: 서비스 테스트 통과를 확인한다**

Run: `./gradlew test --tests "com.juiceplan.budget.BudgetServiceTest"`
Expected: PASS (10개 테스트)

- [ ] **Step 5: API 테스트를 쓴다**

`src/test/kotlin/com/juiceplan/budget/BudgetApiIntegrationTest.kt`:

```kotlin
package com.juiceplan.budget

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BudgetApiIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var itemRepository: BudgetItemRepository
    @Autowired lateinit var budgetService: BudgetService

    private val body = """
        {"name":"오타루 왕복 JR","category":"TRANSIT","paymentMethod":"TRAVEL_LOG",
         "currency":"JPY","amount":3000,"settlement":"PENDING","memo":"자유석"}
    """.trimIndent()

    @BeforeEach
    fun setUp() {
        itemRepository.deleteAll()
        budgetService.saveRate(900)
    }

    @Test
    fun `create returns the saved item with its id`() {
        mockMvc.perform(post("/api/budget/items").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").isNumber)
            .andExpect(jsonPath("$.amount").value(3000))
            .andExpect(jsonPath("$.currency").value("JPY"))
    }

    @Test
    fun `summary reflects the items that were created`() {
        mockMvc.perform(post("/api/budget/items").contentType(MediaType.APPLICATION_JSON).content(body))

        mockMvc.perform(get("/api/budget/summary"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.total.jpy").value(3000))
            .andExpect(jsonPath("$.convertedTotalKrw").value(27000))
            .andExpect(jsonPath("$.rows[0].category").value("TRANSIT"))
    }

    @Test
    fun `update changes the item`() {
        val id = budgetService.create(
            BudgetItemInput("첫 이름", BudgetCategory.FOOD, PaymentMethod.CASH, Currency.JPY, 1000, SettlementStatus.PENDING)
        ).id

        mockMvc.perform(put("/api/budget/items/$id").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("오타루 왕복 JR"))
    }

    @Test
    fun `delete removes the item`() {
        val id = budgetService.create(
            BudgetItemInput("지울 것", BudgetCategory.ETC, PaymentMethod.CASH, Currency.KRW, 0, SettlementStatus.PENDING)
        ).id

        mockMvc.perform(delete("/api/budget/items/$id")).andExpect(status().isOk)

        mockMvc.perform(get("/api/budget/summary")).andExpect(jsonPath("$.count").value(0))
    }

    @Test
    fun `a negative amount is a 400 with a message`() {
        val bad = body.replace("\"amount\":3000", "\"amount\":-1")

        mockMvc.perform(post("/api/budget/items").contentType(MediaType.APPLICATION_JSON).content(bad))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("금액은 0보다 작을 수 없습니다."))
    }

    @Test
    fun `an unknown id is a 404`() {
        mockMvc.perform(put("/api/budget/items/999").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isNotFound)
        mockMvc.perform(delete("/api/budget/items/999"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `saving the rate returns the recomputed summary`() {
        mockMvc.perform(post("/api/budget/items").contentType(MediaType.APPLICATION_JSON).content(body))

        mockMvc.perform(put("/api/budget/rate").contentType(MediaType.APPLICATION_JSON).content("""{"ratePer100Jpy":1000}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ratePer100Jpy").value(1000))
            .andExpect(jsonPath("$.convertedTotalKrw").value(30000))
    }

    @Test
    fun `a non-positive rate is a 400`() {
        mockMvc.perform(put("/api/budget/rate").contentType(MediaType.APPLICATION_JSON).content("""{"ratePer100Jpy":0}"""))
            .andExpect(status().isBadRequest)
    }
}
```

- [ ] **Step 6: 실패를 확인한다**

Run: `./gradlew test --tests "com.juiceplan.budget.BudgetApiIntegrationTest"`
Expected: FAIL — 모든 경로가 404

- [ ] **Step 7: API 컨트롤러를 만든다**

`src/main/kotlin/com/juiceplan/budget/BudgetApiController.kt`:

```kotlin
package com.juiceplan.budget

import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

data class BudgetItemRequest(
    val name: String,
    val category: BudgetCategory,
    val paymentMethod: PaymentMethod,
    val currency: Currency,
    val amount: Int,
    val settlement: SettlementStatus,
    val memo: String? = null
) {
    fun toInput() = BudgetItemInput(name, category, paymentMethod, currency, amount, settlement, memo)
}

data class RateRequest(val ratePer100Jpy: Int)

/**
 * 저장·수정이 엔티티를 그대로 돌려주는 이유는 SourceController 와 같다 — 클라이언트가
 * 생성된 id 를 알아야 목록에 넣고 이후 수정·삭제를 걸 수 있다.
 *
 * 합계는 서버만 낸다. 항목을 고친 뒤 화면은 /api/budget/summary 를 다시 받아온다.
 * 왕복이 한 번 늘지만 계산 규칙이 JS 에 복제되지 않는다.
 */
@RestController
class BudgetApiController(private val budgetService: BudgetService) {

    @GetMapping("/api/budget/summary")
    fun summary(): BudgetSummary = budgetService.summary()

    @PostMapping("/api/budget/items")
    fun create(@RequestBody request: BudgetItemRequest): BudgetItem =
        budgetService.create(request.toInput())

    @PutMapping("/api/budget/items/{id}")
    fun update(@PathVariable id: Long, @RequestBody request: BudgetItemRequest): BudgetItem =
        budgetService.update(id, request.toInput())

    @DeleteMapping("/api/budget/items/{id}")
    fun delete(@PathVariable id: Long) {
        budgetService.delete(id)
    }

    @PutMapping("/api/budget/rate")
    fun saveRate(@RequestBody request: RateRequest): BudgetSummary {
        budgetService.saveRate(request.ratePer100Jpy)
        return budgetService.summary()
    }
}
```

- [ ] **Step 8: 통과를 확인한다**

Run: `./gradlew test --tests "com.juiceplan.budget.*"`
Expected: PASS (전부)

- [ ] **Step 9: 커밋**

```bash
git add src/main/kotlin/com/juiceplan/budget src/test/kotlin/com/juiceplan/budget
git commit -m "feat: 예산 항목 CRUD 와 요약 API"
```

---

## Task 5: 초기 21개 항목 시드

**Files:**
- Create: `src/main/kotlin/com/juiceplan/budget/BudgetSeeder.kt`
- Test: `src/test/kotlin/com/juiceplan/budget/BudgetSeederTest.kt`

**Interfaces:**
- Consumes: `BudgetItemRepository`, `BudgetSettingRepository`, `BudgetItem`, enum 전부, `SETTING_ID`, `DEFAULT_RATE_PER_100_JPY` (Task 3)
- Produces: `BudgetSeeder : ApplicationRunner` — 메서드 `seed()`

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`src/test/kotlin/com/juiceplan/budget/BudgetSeederTest.kt`:

```kotlin
package com.juiceplan.budget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import

@DataJpaTest
@Import(BudgetSeeder::class, BudgetService::class)
class BudgetSeederTest {

    @Autowired lateinit var seeder: BudgetSeeder
    @Autowired lateinit var itemRepository: BudgetItemRepository
    @Autowired lateinit var settingRepository: BudgetSettingRepository
    @Autowired lateinit var budgetService: BudgetService

    // 같은 JVM 의 @SpringBootTest 가 커밋해 둔 항목·설정이 남아 있을 수 있다.
    // 시드는 "비어 있을 때만" 도는 게 핵심이라 빈 상태에서 출발해야 한다.
    @BeforeEach
    fun setUp() {
        itemRepository.deleteAll()
        settingRepository.deleteAll()
    }

    @Test
    fun `an empty budget gets the 21 items we already use`() {
        seeder.seed()

        assertEquals(21L, itemRepository.count())
    }

    @Test
    fun `the seeded budget matches the numbers in the spec`() {
        seeder.seed()

        val summary = budgetService.summary()
        assertEquals(Money(jpy = 17_080, krw = 853_800), summary.total)
        assertEquals(1_007_520, summary.convertedTotalKrw)
        assertEquals(503_760, summary.convertedPerPersonKrw)
        assertEquals(900, summary.ratePer100Jpy)
    }

    @Test
    fun `every category has the number of items the spec lists`() {
        seeder.seed()

        val counts = budgetService.summary().rows.associate { it.category to it.count }
        assertEquals(
            mapOf(
                BudgetCategory.FLIGHT to 1,
                BudgetCategory.HOTEL to 1,
                BudgetCategory.FOOD to 10,
                BudgetCategory.TRANSIT to 4,
                BudgetCategory.ACTIVITY to 1,
                BudgetCategory.SHOPPING to 2,
                BudgetCategory.ETC to 2
            ),
            counts
        )
    }

    @Test
    fun `running the seeder again changes nothing`() {
        seeder.seed()
        val first = itemRepository.findAll().map { it.id }

        seeder.seed()

        assertEquals(first, itemRepository.findAll().map { it.id })
    }

    @Test
    fun `a budget that already has items is left alone`() {
        budgetService.create(
            BudgetItemInput("손으로 넣은 것", BudgetCategory.ETC, PaymentMethod.CASH, Currency.KRW, 5_000, SettlementStatus.PENDING)
        )

        seeder.seed()

        assertEquals(1L, itemRepository.count())
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew test --tests "com.juiceplan.budget.BudgetSeederTest"`
Expected: FAIL — `Unresolved reference: BudgetSeeder`

- [ ] **Step 3: 시드를 만든다**

`src/main/kotlin/com/juiceplan/budget/BudgetSeeder.kt`:

```kotlin
package com.juiceplan.budget

import com.juiceplan.budget.BudgetCategory.ACTIVITY
import com.juiceplan.budget.BudgetCategory.ETC
import com.juiceplan.budget.BudgetCategory.FLIGHT
import com.juiceplan.budget.BudgetCategory.FOOD
import com.juiceplan.budget.BudgetCategory.HOTEL
import com.juiceplan.budget.BudgetCategory.SHOPPING
import com.juiceplan.budget.BudgetCategory.TRANSIT
import com.juiceplan.budget.Currency.JPY
import com.juiceplan.budget.Currency.KRW
import com.juiceplan.budget.PaymentMethod.CASH
import com.juiceplan.budget.PaymentMethod.CREDIT_CARD
import com.juiceplan.budget.PaymentMethod.TRAVEL_LOG
import com.juiceplan.budget.SettlementStatus.DONE
import com.juiceplan.budget.SettlementStatus.NOT_APPLICABLE
import com.juiceplan.budget.SettlementStatus.PENDING
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 예산 화면을 처음 켤 때 이미 쓰던 표를 그대로 채워 넣는다.
 *
 * 항목이 하나라도 있으면 아무것도 하지 않는다. SchemaMigration 과 같은 원칙이라 몇 번을
 * 띄워도 안전하고, 사람이 지운 항목이 다음 부팅에 되살아나지 않는다.
 *
 * 금액이 0인 항목의 통화는 앞으로 실제로 결제할 통화로 넣었다. 일본에서 쓸 식비·쇼핑은
 * 엔화, 한국에서 결제하는 숙박·eSIM·보험은 원화다. 금액이 0이라 합계는 달라지지 않고
 * 화면에서 바로 고칠 수 있다.
 */
@Component
class BudgetSeeder(
    private val itemRepository: BudgetItemRepository,
    private val settingRepository: BudgetSettingRepository
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        seed()
    }

    @Transactional
    fun seed() {
        if (settingRepository.findById(SETTING_ID).isEmpty) {
            settingRepository.save(BudgetSetting(ratePer100Jpy = DEFAULT_RATE_PER_100_JPY))
        }
        if (itemRepository.count() > 0L) return
        itemRepository.saveAll(initialItems())
    }
}

private fun item(
    name: String,
    category: BudgetCategory,
    paymentMethod: PaymentMethod,
    currency: Currency,
    amount: Int,
    settlement: SettlementStatus,
    memo: String? = null
) = BudgetItem(
    name = name,
    category = category,
    paymentMethod = paymentMethod,
    currency = currency,
    amount = amount,
    settlement = settlement,
    memo = memo
)

/** 식비는 "몇 끼를 어떤 수단으로 낼지"만 잡아둔 자리라 이름과 금액이 비어 있다. */
private fun meal(paymentMethod: PaymentMethod) = item("", FOOD, paymentMethod, JPY, 0, PENDING)

private fun initialItems(): List<BudgetItem> = listOf(
    item("왕복 항공권 (2인)", FLIGHT, CREDIT_CARD, KRW, 853_800, NOT_APPLICABLE, "이스타항공 사전 결제 완료(각자 결제)"),
    item("코코 호텔 스스키노 (5박, 2인)", HOTEL, CREDIT_CARD, KRW, 0, PENDING, "조식 미포함, 스스키노역 근처"),
    item("신치토세공항-스스키노 왕복 교통 (2인)", TRANSIT, TRAVEL_LOG, JPY, 5_440, PENDING, "JR 쾌속에어포트 + 지하철"),
    item("오타루 왕복 JR 열차 (2인)", TRANSIT, TRAVEL_LOG, JPY, 3_000, PENDING, "JR 하코다테선 지정석/자유석"),
    item("주말 도니치카 패스 1일권 (2인)", TRANSIT, CASH, JPY, 1_040, PENDING, "2일차 일요일 지하철 무제한"),
    item("기타 시내 지하철/버스 충전 (2인)", TRANSIT, TRAVEL_LOG, JPY, 4_000, PENDING, "IC카드 충전식 사용"),
    meal(TRAVEL_LOG),
    meal(TRAVEL_LOG),
    meal(CASH),
    meal(TRAVEL_LOG),
    meal(TRAVEL_LOG),
    meal(CREDIT_CARD),
    meal(TRAVEL_LOG),
    meal(CASH),
    meal(TRAVEL_LOG),
    item("4일차 오타루 운하 크루즈 (2인)", ACTIVITY, TRAVEL_LOG, JPY, 3_600, PENDING, "운하 크루즈 탑승권"),
    meal(TRAVEL_LOG),
    item("돈키호테 쇼핑 & 기념품", SHOPPING, CREDIT_CARD, JPY, 0, PENDING, "의약품, 화장품, 소품"),
    item("공항 면세점 과자/사케 선물", SHOPPING, CREDIT_CARD, JPY, 0, PENDING, "시로이코이비토, 로이스"),
    item("일본 eSIM 6일권 (2인)", ETC, CREDIT_CARD, KRW, 0, DONE, "매일 2GB eSIM"),
    item("해외 여행자 보험 (2인)", ETC, CREDIT_CARD, KRW, 0, DONE, "기본 플랜"),
)
```

- [ ] **Step 4: 통과를 확인한다**

Run: `./gradlew test --tests "com.juiceplan.budget.BudgetSeederTest"`
Expected: PASS (5개 테스트)

- [ ] **Step 5: 전체 테스트를 돌린다**

Run: `./gradlew test`
Expected: PASS. `BudgetApiIntegrationTest`가 `@SpringBootTest`라 시드가 함께 돌지만, 각 테스트가 `@BeforeEach`에서 `itemRepository.deleteAll()`을 하므로 영향받지 않는다. 실패하면 그 테스트의 `setUp`이 비어 있지 않은지 먼저 본다.

- [ ] **Step 6: 커밋**

```bash
git add src/main/kotlin/com/juiceplan/budget/BudgetSeeder.kt src/test/kotlin/com/juiceplan/budget/BudgetSeederTest.kt
git commit -m "feat: 쓰던 21개 지출 항목을 빈 DB 에 채운다"
```

---

## Task 6: 요약 표와 지출 내역 목록 (읽기 전용)

**Files:**
- Create: `src/main/resources/static/js/budget-types.js`
- Create: `src/main/resources/static/js/budget-summary.js`
- Create: `src/main/resources/static/js/budget-list.js`
- Modify: `src/main/resources/static/js/budget.js`
- Modify: `src/main/resources/static/js/api.js`
- Modify: `src/main/resources/templates/budget/index.html`
- Modify: `src/main/kotlin/com/juiceplan/budget/BudgetPageController.kt`
- Modify: `src/main/resources/static/css/style.css`
- Test: `src/test/kotlin/com/juiceplan/nav/SectionNavigationIntegrationTest.kt` (테스트 추가)

**Interfaces:**
- Consumes: `BudgetService.list()`, `BudgetService.summary()` (Task 4), Task 2의 `#view-summary` / `#view-list`
- Produces:
  - 인라인 전역 `window.BUDGET_ITEMS` (배열), `window.BUDGET_SUMMARY` (객체)
  - `window.BudgetTypes` — `categoryLabel(k)`, `categoryToken(k)`, `method(k)`, `settlement(k)`, `settlementClass(k)`, `money(currency, amount)`, `moneyLines(currencies, money)`
  - `window.ViewSummary` / `window.ViewList` — 각각 `init()`, `show()` (셸의 `ViewAdd`/`ViewDay` 와 같은 이름 규칙. 데이터 전역 `BUDGET_SUMMARY` 와 헷갈리지 않게 `Budget` 접두어를 쓰지 않는다)
  - `window.BudgetSection.refresh()` — 지금 탭을 다시 그린다
  - `window.Api.budgetSummary()`, `window.Api.saveBudgetRate(ratePer100Jpy)`

- [ ] **Step 1: 페이지가 데이터를 싣도록 컨트롤러를 고친다**

`BudgetPageController` 에 서비스를 주입하고 모델 값을 더한다. 바뀌는 부분만:

```kotlin
@Controller
class BudgetPageController(private val budgetService: BudgetService) {

    @GetMapping("/budget")
    fun root(): RedirectView = RedirectView(Nav.section(SECTION).defaultPath())

    @GetMapping("/budget/{tab}")
    fun page(@PathVariable tab: String, model: Model): Any {
        val section = Nav.section(SECTION)
        if (!section.has(tab)) return RedirectView(section.defaultPath())

        // 첫 화면에서 API 를 한 번 더 부르지 않도록 셸(SOURCES)과 같은 방식으로 실어 보낸다
        model.addAttribute("items", budgetService.list())
        model.addAttribute("summary", budgetService.summary())
        model.addNav(SECTION, tab)
        return "budget/index"
    }
}
```

- [ ] **Step 2: 실려 나가는지 확인하는 테스트를 더한다**

`SectionNavigationIntegrationTest` 에 추가한다:

```kotlin
    @Autowired lateinit var budgetService: com.juiceplan.budget.BudgetService

    // 테스트용 H2 는 DB_CLOSE_DELAY=-1 이라 같은 스프링 컨텍스트를 쓰는 다른 테스트 클래스와
    // DB 를 나눠 쓴다. 환율을 바꾸는 테스트가 먼저 돌았을 수 있으므로 되돌려 놓는다.
    @BeforeEach
    fun resetRate() {
        budgetService.saveRate(900)
    }

    @Test
    fun `the budget page embeds its items and summary for the client`() {
        // Thymeleaf 의 JS 인라이닝은 한글을 \uXXXX 로 이스케이프하므로 ASCII 로 남는 필드만 본다
        mockMvc.perform(get("/budget/summary"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("var BUDGET_ITEMS =")))
            .andExpect(content().string(containsString("\"ratePer100Jpy\":900")))
    }
```

`import org.junit.jupiter.api.BeforeEach` 를 함께 넣는다.

Run: `./gradlew test --tests "com.juiceplan.nav.SectionNavigationIntegrationTest"`
Expected: FAIL — `BUDGET_ITEMS` 가 없다

- [ ] **Step 3: 분류 이름 JS를 만든다**

`src/main/resources/static/js/budget-types.js`:

```js
// 예산 분류 이름과 색 한 곳. 서버의 enum 값과 키를 맞춘다. 뷰마다 이름을 따로 쓰면
// 종류를 늘릴 때 한 군데씩 빠뜨리고, 빠뜨린 자리는 조용히 빈칸이 된다.
// (place-types.js 와 같은 역할이다)
window.BudgetTypes = (function () {
    const CATEGORIES = {
        FLIGHT:   { label: '항공 (Flight)', token: '--cat-flight' },
        HOTEL:    { label: '숙박 (Hotel)', token: '--cat-hotel' },
        FOOD:     { label: '식비 (Food & Dining)', token: '--cat-food' },
        TRANSIT:  { label: '교통 (Transit)', token: '--cat-transit' },
        ACTIVITY: { label: '관광/입장료 (Activities)', token: '--cat-activity' },
        SHOPPING: { label: '쇼핑/기념품 (Shopping)', token: '--cat-shopping' },
        ETC:      { label: '기타 (eSIM/보험 등)', token: '--cat-etc' },
    };
    const METHODS = { CREDIT_CARD: '신용카드', TRAVEL_LOG: '트래블로그', CASH: '현금' };
    const SETTLEMENTS = {
        PENDING: { label: '미정산', suffix: 'pending' },
        DONE: { label: '완료', suffix: 'done' },
        NOT_APPLICABLE: { label: '해당없음', suffix: 'na' },
    };
    const SYMBOLS = { JPY: '¥', KRW: '₩' };

    // 서버에 새 값이 생겼는데 화면이 아직 모를 때 빈칸을 내지 않기 위한 대비책
    const CATEGORY_FALLBACK = { label: '기타', token: '--cat-etc' };
    const SETTLEMENT_FALLBACK = { label: '미정산', suffix: 'pending' };

    function category(key) { return CATEGORIES[key] || CATEGORY_FALLBACK; }
    function settlement(key) { return SETTLEMENTS[key] || SETTLEMENT_FALLBACK; }

    function money(currency, amount) {
        return (SYMBOLS[currency] || '') + Number(amount || 0).toLocaleString('ko-KR');
    }

    /**
     * 통화별 금액을 줄바꿈으로 쌓는다. 금액이 전부 0인 카테고리는 합계만 봐서는
     * ¥0 인지 ₩0 인지 알 수 없어 서버가 준 currencies 를 따라간다.
     */
    function moneyLines(currencies, amounts) {
        if (!currencies || currencies.length === 0) return money('KRW', 0);
        return currencies
            .map((c) => money(c, c === 'JPY' ? amounts.jpy : amounts.krw))
            .join('<br>');
    }

    return {
        categoryLabel: (k) => category(k).label,
        categoryToken: (k) => category(k).token,
        method: (k) => METHODS[k] || k,
        settlementLabel: (k) => settlement(k).label,
        settlementClass: (k) => 'badge--settle-' + settlement(k).suffix,
        money,
        moneyLines,
    };
})();
```

- [ ] **Step 4: 요약 탭을 만든다**

`src/main/resources/static/js/budget-summary.js`:

```js
// 예산 요약 탭: 환율 입력 + 카테고리별 합계 표.
// 합계는 서버(BudgetTotals)만 계산한다. 여기서는 그리기만 한다.
window.ViewSummary = (function () {
    const T = () => window.BudgetTypes;

    function rowHtml(row) {
        return '<tr>' +
            `<th scope="row">${T().categoryLabel(row.category)}</th>` +
            `<td class="num">${row.count}</td>` +
            `<td class="num">${T().moneyLines(row.currencies, row.total)}</td>` +
            `<td class="num">${T().moneyLines(row.currencies, row.perPerson)}</td>` +
            '</tr>';
    }

    function tableHtml(summary) {
        if (summary.rows.length === 0) {
            return '<p class="card muted">아직 지출 항목이 없습니다.</p>';
        }
        return '<table class="budget-table">' +
            '<thead><tr><th>카테고리</th><th class="num">건수</th>' +
            '<th class="num">2인 합계</th><th class="num">1인당</th></tr></thead>' +
            `<tbody>${summary.rows.map(rowHtml).join('')}</tbody>` +
            '<tfoot><tr>' +
                '<th scope="row">합계</th>' +
                `<td class="num">${summary.count}</td>` +
                `<td class="num">${T().moneyLines(summary.currencies, summary.total)}</td>` +
                `<td class="num">${T().moneyLines(summary.currencies, summary.perPerson)}</td>` +
            '</tr></tfoot></table>' +
            `<p class="muted budget-converted">100엔 = ₩${summary.ratePer100Jpy} 기준 ` +
            `<strong>대략 ${T().money('KRW', summary.convertedTotalKrw)}</strong>` +
            ` · 1인 ${T().money('KRW', summary.convertedPerPersonKrw)}</p>`;
    }

    function rateHtml(summary) {
        return '<div class="card budget-rate">' +
            '<label for="budgetRate">환율</label>' +
            '<div class="budget-rate__row">' +
                '<span>100엔 = ₩</span>' +
                `<input type="number" id="budgetRate" min="1" step="1" value="${summary.ratePer100Jpy}">` +
            '</div>' +
            '<p class="error" id="rateError" hidden></p>' +
            '</div>';
    }

    async function saveRate(value) {
        const errorEl = document.getElementById('rateError');
        errorEl.hidden = true;
        try {
            window.BUDGET_SUMMARY = await window.Api.saveBudgetRate(Number(value));
            show();
        } catch (e) {
            errorEl.textContent = e.message;
            errorEl.hidden = false;
        }
    }

    function init() {
        // 이 탭의 내용은 매번 새로 그려지므로 이벤트는 바깥 상자에 한 번만 건다
        document.getElementById('view-summary').addEventListener('change', (e) => {
            if (e.target.id === 'budgetRate') saveRate(e.target.value);
        });
    }

    function show() {
        const summary = window.BUDGET_SUMMARY;
        document.getElementById('view-summary').innerHTML =
            rateHtml(summary) + `<div class="card">${tableHtml(summary)}</div>`;
    }

    return { init, show };
})();
```

- [ ] **Step 5: 내역 탭을 만든다 (읽기 전용)**

`src/main/resources/static/js/budget-list.js`:

```js
// 지출 내역 탭: 카테고리로 묶은 카드 목록. 8열 표를 모바일에 밀어 넣지 않는다.
window.ViewList = (function () {
    const T = () => window.BudgetTypes;

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    /** 1인당은 나눗셈 한 번이라 여기서 낸다. 카테고리 합계는 서버가 준 값을 쓴다. */
    function perPerson(item) {
        return T().money(item.currency, Math.floor(item.amount / 2));
    }

    function cardHtml(item) {
        const name = item.name
            ? `<strong>${escapeHtml(item.name)}</strong>`
            : '<strong class="muted">(이름 없음)</strong>';
        return `<div class="card budget-card" data-id="${item.id}">` +
            `<div class="budget-card__head">${name}</div>` +
            '<div class="budget-card__meta">' +
                `<span class="muted">${T().method(item.paymentMethod)}</span>` +
                `<span class="badge ${T().settlementClass(item.settlement)}">${T().settlementLabel(item.settlement)}</span>` +
            '</div>' +
            '<div class="budget-card__amount">' +
                `<strong>${T().money(item.currency, item.amount)}</strong>` +
                `<span class="muted">1인 ${perPerson(item)}</span>` +
            '</div>' +
            (item.memo ? `<p class="muted budget-card__memo">${escapeHtml(item.memo)}</p>` : '') +
            '</div>';
    }

    function groupHtml(row) {
        const items = window.BUDGET_ITEMS.filter((i) => i.category === row.category);
        return '<div class="budget-group">' +
            '<h2 class="budget-group__head">' +
                `${T().categoryLabel(row.category)}` +
                `<span class="muted">${row.count}건 · ${T().moneyLines(row.currencies, row.total)}</span>` +
            '</h2>' +
            items.map(cardHtml).join('') +
            '</div>';
    }

    function init() {
        // 편집은 다음 단계에서 붙인다
    }

    function show() {
        const rows = window.BUDGET_SUMMARY.rows;
        document.getElementById('view-list').innerHTML = rows.length === 0
            ? '<p class="card muted">아직 지출 항목이 없습니다.</p>'
            : rows.map(groupHtml).join('');
    }

    return { init, show };
})();
```

> `init()` 이 비어 있는 건 다음 태스크에서 채우기 때문이다. 지금 지우면 `budget.js` 가
> 두 뷰를 다르게 다뤄야 하므로 빈 채로 둔다.

- [ ] **Step 6: 라우터가 뷰를 그리게 고친다**

`budget.js` 에서 `let current = null;` 아래에 더한다:

```js
    const VIEWS = {
        summary: window.ViewSummary,
        list: window.ViewList,
    };
```

`show(tab)` 의 마지막 줄(`current = tab;`) 뒤에 더한다:

```js
        VIEWS[tab].show();
```

파일 끝의 `route();` 바로 앞에 더한다:

```js
    // 데이터가 바뀌었을 때 지금 탭을 다시 그리게 하는 통로
    window.BudgetSection = {
        refresh: () => { if (current) VIEWS[current].show(); },
        currentTab: () => current,
    };

    // 각 뷰의 한 번뿐인 초기화(이벤트 바인딩)를 먼저 돌린다
    Object.values(VIEWS).forEach((v) => v.init());
```

- [ ] **Step 7: API 메서드를 더한다**

`api.js` 의 `return {` 블록 안, `parseLink` 줄 아래에 더한다:

```js
        budgetSummary: () => send('GET', '/api/budget/summary'),
        saveBudgetRate: (ratePer100Jpy) => send('PUT', '/api/budget/rate', { ratePer100Jpy }),
```

- [ ] **Step 8: 페이지에 데이터와 스크립트를 싣는다**

`budget/index.html` 의 `<footer>` 아래를 바꾼다:

```html
<footer th:replace="~{fragments/tabbar :: tabbar}"></footer>

<script th:inline="javascript">
    /*<![CDATA[*/
    var BUDGET_ITEMS = /*[[${items}]]*/ [];
    var BUDGET_SUMMARY = /*[[${summary}]]*/ {};
    /*]]>*/
</script>

<script th:src="@{/js/budget-types.js}"></script>
<script th:src="@{/js/api.js}"></script>
<script th:src="@{/js/budget-summary.js}"></script>
<script th:src="@{/js/budget-list.js}"></script>
<script th:src="@{/js/budget.js}"></script>
</body>
```

- [ ] **Step 9: CSS를 더한다**

`style.css` 의 `:root` 토큰 블록 끝(`--on-accent` 줄 아래)에:

```css
  /* 예산 카테고리 7색 — 도넛과 그룹 머리글에 쓴다 */
  --cat-flight: #4F6BED;
  --cat-hotel: #9B6DD6;
  --cat-food: #FF7A59;
  --cat-transit: #2BB0A0;
  --cat-activity: #E8B33C;
  --cat-shopping: #E0619B;
  --cat-etc: #7B8794;
```

`@media (prefers-color-scheme: dark)` 의 `:root` 블록 끝에:

```css
    --cat-flight: #7C90F5;
    --cat-hotel: #B794E8;
    --cat-food: #FF9273;
    --cat-transit: #48C7B7;
    --cat-activity: #F0C662;
    --cat-shopping: #F084B4;
    --cat-etc: #9AA1AD;
```

`.badge--reservation` 규칙 아래에:

```css
/* 정산 상태 */
.badge--settle-done { background: var(--attraction); }
.badge--settle-na { background: var(--muted); }
.badge--settle-pending {
  background: transparent;
  color: var(--muted);
  border: 1px solid var(--border);
  font-weight: 500;
}
```

파일 끝(`/* ---- 로그인 ---- */` 블록 앞)에:

```css
/* ============================================================
   예산
   ============================================================ */
.budget-rate { display: flex; flex-direction: column; }
.budget-rate label { margin-top: 0; }
.budget-rate__row { display: flex; align-items: center; gap: 8px; }
/* 환율은 네 자리면 충분하다. 전체 폭으로 늘리면 입력칸만 덩그러니 커 보인다. */
.budget-rate__row input { width: 100px; }

.budget-table { width: 100%; border-collapse: collapse; font-size: 14px; }
.budget-table th,
.budget-table td {
  padding: 8px 4px;
  border-bottom: 1px solid var(--border);
  text-align: left; vertical-align: top;
  font-weight: 400;
}
.budget-table thead th { color: var(--muted); font-size: 12px; }
/* 숫자는 오른쪽으로 맞춰야 자릿수를 눈으로 비교할 수 있다 */
.budget-table .num { text-align: right; white-space: nowrap; }
.budget-table tfoot th,
.budget-table tfoot td { font-weight: 700; border-bottom: 0; }
.budget-converted { margin: 12px 0 0; }

.budget-group__head {
  display: flex; align-items: baseline; justify-content: space-between;
  gap: 8px; flex-wrap: wrap;
  font-size: 15px;
  margin: 16px 16px 4px;
}
.budget-card__head { margin-bottom: 4px; }
.budget-card__meta { display: flex; align-items: center; gap: 8px; }
.budget-card__amount {
  display: flex; align-items: baseline; gap: 10px;
  margin-top: 6px;
}
.budget-card__memo { margin: 6px 0 0; white-space: pre-wrap; }
```

- [ ] **Step 10: 테스트를 돌린다**

Run: `./gradlew test`
Expected: PASS (전부)

- [ ] **Step 11: 눈으로 확인한다**

Run: `./gradlew bootRun`

`http://localhost:8080/budget` 을 연다.

1. 요약 탭에 `100엔 = ₩900` 입력칸과 7행 + 합계 행짜리 표가 보인다
2. 항공 행은 `₩853,800` / `₩426,900`, 교통 행은 `¥13,480` / `¥6,740` 이다
3. 식비 행은 `¥0` 이다 (`₩0` 이 아니다)
4. 합계 행은 `¥17,080` 과 `₩853,800` 두 줄이고, 그 아래 "대략 ₩1,007,520 · 1인 ₩503,760"
5. 환율을 `1000` 으로 고치고 포커스를 옮기면 표의 통화별 합계는 그대로고 "대략" 줄만 바뀐다. 900으로 되돌린다
6. 지출 내역 탭에 카테고리별 머리글과 카드 21장이 보이고, 이름 없는 식비는 `(이름 없음)` 으로 흐리게 나온다
7. 시스템 다크모드를 켜고 두 탭을 다시 본다 — 글자와 배경이 모두 읽힌다

- [ ] **Step 12: 커밋**

```bash
git add -A
git commit -m "feat: 예산 요약 표와 지출 내역 목록"
```

---

## Task 7: 카테고리 비중 도넛

**Files:**
- Create: `src/main/resources/static/js/donut.js`
- Modify: `src/main/resources/static/js/budget-summary.js`
- Modify: `src/main/resources/templates/budget/index.html`
- Modify: `src/main/resources/static/css/style.css`

**Interfaces:**
- Consumes: `window.BUDGET_SUMMARY.rows[].convertedKrw`, `.convertedTotalKrw`, `window.BudgetTypes.categoryToken` (Task 6)
- Produces: `window.Donut.svg(slices, centerLabel)` — `slices` 는 `{ label, value, token }` 배열, SVG 문자열을 돌려준다

- [ ] **Step 1: 도넛을 만든다**

`src/main/resources/static/js/donut.js`:

```js
// 비중 도넛. 차트 라이브러리 없이 SVG arc 를 직접 그린다. 이 앱은 구글맵 말고 CDN 을
// 쓰지 않고, 색을 CSS 토큰으로 두면 다크모드가 저절로 따라온다.
window.Donut = (function () {
    const SIZE = 160;
    const CX = 80, CY = 80;
    const R = 58;          // 고리의 중심선 반지름
    const WIDTH = 22;

    function point(fraction) {
        // 0 = 12시, 시계방향
        const angle = fraction * 2 * Math.PI - Math.PI / 2;
        return [CX + R * Math.cos(angle), CY + R * Math.sin(angle)];
    }

    function arcPath(from, to) {
        const [x0, y0] = point(from);
        const [x1, y1] = point(to);
        const large = to - from > 0.5 ? 1 : 0;
        return `M ${x0.toFixed(2)} ${y0.toFixed(2)} A ${R} ${R} 0 ${large} 1 ${x1.toFixed(2)} ${y1.toFixed(2)}`;
    }

    function strokeFor(token) {
        return `stroke="var(${token})" stroke-width="${WIDTH}" fill="none"`;
    }

    /**
     * slices: [{ label, value, token }] — value 는 같은 단위의 양수.
     * 값이 0인 조각은 부르는 쪽에서 걸러 보낸다.
     */
    function svg(slices, centerLabel) {
        const total = slices.reduce((sum, s) => sum + s.value, 0);
        if (total <= 0) return '';

        // 조각이 하나뿐이면 시작점과 끝점이 같아 호가 아무것도 그리지 않는다. 원을 그린다.
        const shapes = slices.length === 1
            ? `<circle cx="${CX}" cy="${CY}" r="${R}" ${strokeFor(slices[0].token)}></circle>`
            : slices.map((slice, i) => {
                const from = slices.slice(0, i).reduce((sum, s) => sum + s.value, 0) / total;
                const to = from + slice.value / total;
                return `<path d="${arcPath(from, to)}" ${strokeFor(slice.token)} stroke-linecap="butt"></path>`;
            }).join('');

        return `<svg class="donut" viewBox="0 0 ${SIZE} ${SIZE}" role="img" aria-label="카테고리 비중">` +
            shapes +
            `<text x="${CX}" y="${CY}" class="donut__label" text-anchor="middle" dominant-baseline="middle">` +
            `${centerLabel}</text>` +
            '</svg>';
    }

    return { svg };
})();
```

- [ ] **Step 2: 요약 탭에 붙인다**

`budget-summary.js` 의 `rateHtml` 아래에 더한다:

```js
    function chartHtml(summary) {
        const slices = summary.rows
            .filter((r) => r.convertedKrw > 0)
            .map((r) => ({
                label: T().categoryLabel(r.category),
                value: r.convertedKrw,
                token: T().categoryToken(r.category),
            }));

        if (slices.length === 0) {
            return '<p class="card muted">아직 금액이 없습니다. 지출 내역에서 금액을 넣으면 비중이 보입니다.</p>';
        }

        const total = summary.convertedTotalKrw;
        const legend = slices.map((s) =>
            '<span class="donut-legend__item">' +
            `<i class="donut-legend__dot" style="background: var(${s.token})"></i>` +
            `${s.label} ${Math.round((s.value / total) * 100)}%` +
            '</span>').join('');

        return '<div class="card budget-chart">' +
            window.Donut.svg(slices, T().money('KRW', total)) +
            `<div class="donut-legend">${legend}</div>` +
            '</div>';
    }
```

`show()` 를 바꾼다:

```js
    function show() {
        const summary = window.BUDGET_SUMMARY;
        document.getElementById('view-summary').innerHTML =
            rateHtml(summary) + chartHtml(summary) + `<div class="card">${tableHtml(summary)}</div>`;
    }
```

- [ ] **Step 3: 스크립트를 싣는다**

`budget/index.html` 의 `budget-types.js` 줄 아래에:

```html
<script th:src="@{/js/donut.js}"></script>
```

- [ ] **Step 4: CSS를 더한다**

`style.css` 의 예산 블록 끝에:

```css
.budget-chart { display: flex; flex-direction: column; align-items: center; gap: 12px; }
.donut { width: 160px; height: 160px; }
.donut__label { fill: var(--text); font-size: 15px; font-weight: 700; }

.donut-legend {
  display: flex; flex-wrap: wrap; justify-content: center;
  gap: 6px 12px;
  font-size: 12px; color: var(--muted);
}
.donut-legend__item { display: inline-flex; align-items: center; gap: 5px; }
.donut-legend__dot { width: 9px; height: 9px; border-radius: 50%; }
```

- [ ] **Step 5: 눈으로 확인한다**

Run: `./gradlew bootRun`

`http://localhost:8080/budget/summary`:

1. 표 위에 도넛이 있고 가운데에 `₩1,007,520` 이 있다
2. 범례에 항공 85%, 교통 12%, 관광 3% 가 색 점과 함께 나온다 (금액 0인 카테고리는 없다)
3. 환율을 크게 바꾸면(예: 1500) 조각 비율이 눈에 띄게 달라진다. 900으로 되돌린다
4. 다크모드에서 조각 색과 가운데 글자가 모두 보인다
5. 조각이 하나만 남는 경우도 확인한다 — 지출 내역에서 항공을 뺀 나머지 금액이 0이면
   원이 통째로 한 색으로 그려져야 한다 (Task 8 이후에 확인해도 된다)

- [ ] **Step 6: 테스트를 돌리고 커밋**

Run: `./gradlew test`
Expected: PASS

```bash
git add -A
git commit -m "feat: 카테고리 비중 도넛"
```

---

## Task 8: 지출 항목 편집

**Files:**
- Modify: `src/main/resources/templates/budget/index.html`
- Modify: `src/main/resources/static/js/budget-list.js`
- Modify: `src/main/resources/static/js/budget.js`
- Modify: `src/main/resources/static/js/api.js`
- Modify: `src/main/resources/static/css/style.css`

**Interfaces:**
- Consumes: `window.Api.createBudgetItem/updateBudgetItem/deleteBudgetItem/budgetSummary`, `window.BudgetSection.refresh()` (Task 6)
- Produces: `#budgetSheet`, `#budgetBackdrop`, `#budgetForm`, `#addBudgetBtn` 엘리먼트

- [ ] **Step 1: 시트 마크업을 더한다**

`budget/index.html` 의 `</div>`(section-body) 와 `<footer>` 사이에:

```html
<button type="button" class="fab" id="addBudgetBtn" aria-label="지출 추가" hidden>+</button>

<!-- ── 지출 추가/수정 시트 ── -->
<div class="sheet__backdrop" id="budgetBackdrop"></div>
<div class="sheet" id="budgetSheet">
    <h3 id="budgetSheetTitle">새 지출 추가</h3>

    <!-- 금액 규칙은 서버가 갖는다. 브라우저 기본 검증을 켜두면 음수를 넣었을 때
         submit 자체가 막혀 서버의 400 메시지를 시트에 띄울 기회가 없어진다.
         min="0" 은 스피너 화살표의 하한으로만 남긴다. -->
    <form id="budgetForm" novalidate>
        <label for="budgetName">이름</label>
        <input type="text" id="budgetName" name="name" placeholder="예: 오타루 왕복 JR 열차 (2인)">

        <label for="budgetCategory">카테고리</label>
        <select id="budgetCategory" name="category">
            <option value="FLIGHT">항공 (Flight)</option>
            <option value="HOTEL">숙박 (Hotel)</option>
            <option value="FOOD">식비 (Food &amp; Dining)</option>
            <option value="TRANSIT">교통 (Transit)</option>
            <option value="ACTIVITY">관광/입장료 (Activities)</option>
            <option value="SHOPPING">쇼핑/기념품 (Shopping)</option>
            <option value="ETC">기타 (eSIM/보험 등)</option>
        </select>

        <label for="budgetMethod">결제 수단</label>
        <select id="budgetMethod" name="paymentMethod">
            <option value="CREDIT_CARD">신용카드</option>
            <option value="TRAVEL_LOG">트래블로그</option>
            <option value="CASH">현금</option>
        </select>

        <label>금액 (2인 총액)</label>
        <div class="budget-amount">
            <div class="budget-amount__currency">
                <label class="inline"><input type="radio" name="currency" value="JPY" checked> ¥ 엔</label>
                <label class="inline"><input type="radio" name="currency" value="KRW"> ₩ 원</label>
            </div>
            <input type="number" id="budgetAmount" name="amount" min="0" step="1" value="0">
        </div>
        <p class="muted" id="budgetPerPerson">1인 ¥0</p>

        <fieldset>
            <legend>정산 여부</legend>
            <label class="inline"><input type="radio" name="settlement" value="PENDING" checked> 미정산</label>
            <label class="inline"><input type="radio" name="settlement" value="DONE"> 완료</label>
            <label class="inline"><input type="radio" name="settlement" value="NOT_APPLICABLE"> 해당없음</label>
        </fieldset>

        <label for="budgetMemo">비고</label>
        <textarea id="budgetMemo" name="memo" rows="2" placeholder="예: 지정석/자유석"></textarea>

        <p id="budgetError" class="error" style="display:none; margin:8px 0 0;"></p>

        <button type="submit" class="btn btn--primary btn--block" id="budgetSubmit" style="margin-top:16px;">저장</button>
        <button type="button" class="btn btn--danger btn--block" id="budgetDelete" style="margin-top:8px;" hidden>삭제</button>
        <button type="button" class="btn btn--ghost btn--block" id="budgetSheetClose" style="margin-top:8px;">닫기</button>
    </form>
</div>
```

- [ ] **Step 2: API 메서드를 더한다**

`api.js` 의 `saveBudgetRate` 줄 아래에:

```js
        createBudgetItem: (payload) => send('POST', '/api/budget/items', payload),
        updateBudgetItem: (id, payload) => send('PUT', `/api/budget/items/${id}`, payload),
        deleteBudgetItem: (id) => send('DELETE', `/api/budget/items/${id}`),
```

- [ ] **Step 3: 내역 탭에 편집을 붙인다**

`budget-list.js` 의 `init()` 을 아래로 바꾸고, 그 위에 필요한 함수를 더한다:

```js
    let editingId = null;

    const sheet = () => document.getElementById('budgetSheet');
    const backdrop = () => document.getElementById('budgetBackdrop');
    const form = () => document.getElementById('budgetForm');

    function currencyValue() {
        return form().querySelector('input[name="currency"]:checked').value;
    }

    /** 저장하지 않는 미리보기. 통화나 금액을 고치면 따라 바뀐다. */
    function renderPerPerson() {
        const amount = Number(document.getElementById('budgetAmount').value || 0);
        document.getElementById('budgetPerPerson').textContent =
            '1인 ' + T().money(currencyValue(), Math.floor(Math.max(0, amount) / 2));
    }

    function openSheet(item) {
        editingId = item ? item.id : null;
        const f = form();
        f.reset();
        document.getElementById('budgetError').style.display = 'none';

        document.getElementById('budgetName').value = item ? item.name : '';
        document.getElementById('budgetCategory').value = item ? item.category : 'FOOD';
        document.getElementById('budgetMethod').value = item ? item.paymentMethod : 'TRAVEL_LOG';
        document.getElementById('budgetAmount').value = item ? item.amount : 0;
        document.getElementById('budgetMemo').value = (item && item.memo) || '';
        f.querySelectorAll('input[name="currency"]').forEach((r) => {
            r.checked = r.value === (item ? item.currency : 'JPY');
        });
        f.querySelectorAll('input[name="settlement"]').forEach((r) => {
            r.checked = r.value === (item ? item.settlement : 'PENDING');
        });

        document.getElementById('budgetSheetTitle').textContent = item ? '지출 수정' : '새 지출 추가';
        document.getElementById('budgetSubmit').textContent = item ? '수정 저장' : '저장';
        document.getElementById('budgetDelete').hidden = !item;
        renderPerPerson();

        sheet().classList.add('sheet--open');
        backdrop().classList.add('sheet--open');
    }

    function closeSheet() {
        sheet().classList.remove('sheet--open');
        backdrop().classList.remove('sheet--open');
        editingId = null;
    }

    function readForm() {
        const f = form();
        return {
            name: document.getElementById('budgetName').value,
            category: document.getElementById('budgetCategory').value,
            paymentMethod: document.getElementById('budgetMethod').value,
            currency: currencyValue(),
            amount: Number(document.getElementById('budgetAmount').value || 0),
            settlement: f.querySelector('input[name="settlement"]:checked').value,
            memo: document.getElementById('budgetMemo').value || null,
        };
    }

    /** 합계는 서버만 계산한다. 항목을 고쳤으면 요약을 다시 받아온다. */
    async function reload() {
        window.BUDGET_SUMMARY = await window.Api.budgetSummary();
        window.BudgetSection.refresh();
    }

    async function submit(e) {
        e.preventDefault();
        const errorEl = document.getElementById('budgetError');
        errorEl.style.display = 'none';

        try {
            const payload = readForm();
            if (editingId === null) {
                window.BUDGET_ITEMS.push(await window.Api.createBudgetItem(payload));
            } else {
                const updated = await window.Api.updateBudgetItem(editingId, payload);
                const i = window.BUDGET_ITEMS.findIndex((it) => it.id === editingId);
                window.BUDGET_ITEMS[i] = updated;
            }
            closeSheet();
            await reload();
        } catch (err) {
            errorEl.textContent = err.message;
            errorEl.style.display = 'block';
        }
    }

    async function remove() {
        const item = window.BUDGET_ITEMS.find((it) => it.id === editingId);
        const label = item && item.name ? `'${item.name}'을(를)` : '이 항목을';
        if (!confirm(`${label} 삭제하시겠습니까?`)) return;

        try {
            await window.Api.deleteBudgetItem(editingId);
            window.BUDGET_ITEMS = window.BUDGET_ITEMS.filter((it) => it.id !== editingId);
            closeSheet();
            await reload();
        } catch (err) {
            alert(err.message);
        }
    }

    function init() {
        // 카드는 매번 새로 그려지므로 클릭은 바깥 상자에서 한 번만 받는다
        document.getElementById('view-list').addEventListener('click', (e) => {
            const card = e.target.closest('.budget-card');
            if (!card) return;
            openSheet(window.BUDGET_ITEMS.find((it) => it.id === Number(card.dataset.id)));
        });

        document.getElementById('addBudgetBtn').addEventListener('click', () => openSheet(null));
        document.getElementById('budgetSheetClose').addEventListener('click', closeSheet);
        document.getElementById('budgetBackdrop').addEventListener('click', closeSheet);
        document.getElementById('budgetDelete').addEventListener('click', remove);
        document.getElementById('budgetForm').addEventListener('submit', submit);
        document.getElementById('budgetForm').addEventListener('input', renderPerPerson);
        document.getElementById('budgetForm').addEventListener('change', renderPerPerson);
    }
```

- [ ] **Step 4: FAB이 내역 탭에서만 보이게 한다**

`budget.js` 의 `show(tab)` 안, `VIEWS[tab].show();` 앞에 더한다:

```js
        document.getElementById('addBudgetBtn').hidden = tab !== 'list';
```

- [ ] **Step 5: CSS를 더한다**

`style.css` 의 예산 블록 끝에:

```css
/* 카드를 누르면 편집 시트가 열린다는 걸 손끝에 알린다 */
.budget-card { cursor: pointer; }

.budget-amount { display: flex; align-items: center; gap: 12px; }
.budget-amount__currency { display: flex; gap: 12px; flex: 0 0 auto; }
.budget-amount input { flex: 1 1 auto; }
#budgetPerPerson { margin: 6px 0 0; }
```

- [ ] **Step 6: 눈으로 확인한다**

Run: `./gradlew bootRun`

`http://localhost:8080/budget/list`:

1. 오른쪽 아래 `+` 버튼이 보인다. 요약 탭으로 옮기면 사라지고 돌아오면 다시 나온다
2. `+` 를 눌러 이름 `테스트 지출`, 카테고리 `식비`, 엔화 `3000` 을 넣으면 아래에 `1인 ¥1,500` 이 즉시 보인다
3. 저장하면 식비 그룹에 카드가 붙고, 그룹 머리글 건수가 11건으로, 요약 탭의 식비 행이 `¥3,000` 으로 바뀐다
4. 그 카드를 눌러 통화를 원으로 바꾸고 `20000` 으로 저장하면, 식비 행이 `¥0` 과 `₩20,000` 두 줄이 된다
5. 다시 눌러 삭제하면 확인 창이 뜨고, 지우면 식비가 10건으로 돌아온다
6. 금액에 `-1` 을 넣고 저장하면 시트 안에 "금액은 0보다 작을 수 없습니다." 가 뜨고 시트가 닫히지 않는다
7. 이름을 비운 채 저장하면 `(이름 없음)` 카드로 들어간다
8. 백드롭을 누르면 시트가 닫힌다

- [ ] **Step 7: 테스트를 돌리고 커밋**

Run: `./gradlew test`
Expected: PASS

```bash
git add -A
git commit -m "feat: 지출 항목 추가·수정·삭제"
```

---

## Task 9: 마무리 검증

**Files:**
- Modify: 없음 (문제가 나오면 고친다)

- [ ] **Step 1: 전체 테스트**

Run: `./gradlew clean test`
Expected: PASS — 실패가 있으면 여기서 멈추고 고친다

- [ ] **Step 2: 기존 화면이 그대로인지 본다**

Run: `./gradlew bootRun`

1. `/schd/add` — 장소 목록, 필터 칩, `+` 버튼이 예전과 같다
2. `/schd/plan` — 지도 핸들을 3단계로 끌어올렸다 내렸다 해도 동작한다
3. `/schd/day` — 타임테이블이 그려진다
4. 세 탭을 오갈 때 지도가 다시 만들어지지 않는다 (깜빡임이 없다)

- [ ] **Step 3: 실제 DB로 시드를 확인한다**

`data/juice-plan.mv.db` 에 이미 예산 항목이 없다면, 앱을 처음 띄웠을 때 21개가 들어와 있어야 한다. 이미 있었다면 그대로여야 한다.

`/budget/summary` 에서 합계가 `¥17,080` / `₩853,800` / 대략 `₩1,007,520` 인지 본다.

- [ ] **Step 4: 앱을 껐다 켜도 그대로인지 본다**

`bootRun` 을 멈추고 다시 띄운 뒤 `/budget/list` 를 연다. 항목 수가 그대로고 21개가 두 배로 늘지 않았다.

- [ ] **Step 5: 커밋할 게 남았으면 커밋**

```bash
git status
```

---

## Self-Review 기록

**스펙 커버리지**

| 스펙 절 | 태스크 |
|---|---|
| 1. 라우팅 (섹션 정의, 서버 라우트, 탭바 프래그먼트) | Task 1, 2 |
| 2. 데이터 모델 (엔티티, enum, 환율) | Task 3 |
| 3. 계산 규칙 | Task 3 |
| 4. API + 검증 | Task 4 |
| 5. summary 화면 (환율, 도넛, 표) | Task 6, 7 |
| 6. list 화면 (카드, 편집 시트) | Task 6, 8 |
| 7. check 스텁 | Task 2 |
| 8. 초기 데이터 21행 | Task 5 |
| 9. 파일 목록 | 위 "파일 구조" |
| 10. 테스트 | Task 1·2·3·4·5 의 테스트 단계 |
| 11. 위험 (탭바 교체가 셸을 건드림) | Task 2 Step 1 의 `data-tab` 테스트, Task 9 Step 2 |

**스펙과 달라진 곳**

- 스펙 4절은 요약 API 없이 클라이언트가 합계를 계산하는 안이었다. 이 프로젝트에 JS 테스트 장치가 없어 복제된 계산을 아무도 지켜주지 못하므로, `GET /api/budget/summary` 를 두고 서버만 계산하도록 스펙을 고쳤다(같은 커밋에 반영).
- 환율 단위를 "1엔당 BigDecimal"에서 "100엔당 Int"로 바꿨다. 화면·API·DB가 한 단위를 쓰므로 변환 함수가 사라진다.
- `CategoryTotal.currencies` 를 더했다. 금액이 전부 0인 카테고리를 `¥0` 으로 적을지 `₩0` 으로 적을지는 합계만으로 알 수 없다.
- 죽은 `templates/fragments/layout.html` 을 지운다(Task 2). 새 탭바와 역할이 겹쳐 남겨두면 헷갈린다.
