# 체크리스트(check) 섹션 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `check` 섹션의 스텁을 걷어내고 쇼핑 목록·준비물·할일 세 체크리스트를 만든다.

**Architecture:** 세 목록이 전부 "이름 + 체크 + 메모"로 같은 모양이라 엔티티 하나에 목록 구분자 enum만 둔다. 화면도 렌더러 하나를 세 탭이 공유하고, 어느 목록인지만 다르다. 섹션 구조(`Nav.kt`, 탭바 프래그먼트, 지도 없는 독립 페이지, 섹션 내 `pushState` 라우팅)는 예산 섹션이 만들어 둔 것을 그대로 쓴다.

**Tech Stack:** Kotlin 1.9.24 / Spring Boot 3.3.2 (Web, Thymeleaf, Data JPA) / H2 / 바닐라 JS (빌드 도구 없음) / JUnit 5 + MockMvc

**Spec:** `docs/superpowers/specs/2026-08-17-check-section-design.md`

## Global Constraints

- **JS 빌드 도구도 npm 도 없다.** 모든 프런트 코드는 `src/main/resources/static/js/` 아래 평범한 `<script>` 파일이고 `window.Xxx` + IIFE 패턴을 따른다. 외부 CDN 은 구글맵 말고 쓰지 않는다.
- **JS 테스트 장치가 없다.** 검증이 필요한 로직은 반드시 Kotlin 쪽에 둔다.
- **모든 JPA enum 필드는 `@Enumerated(EnumType.STRING)` + `@JdbcTypeCode(SqlTypes.VARCHAR)`.** H2 네이티브 `ENUM` 컬럼이 만들어지면 `ddl-auto: update` 가 나중에 값을 넓혀주지 않아 기존 DB 가 새 값을 거부한다.
- **CSS 는 `style.css` 상단 토큰(`--bg`, `--surface`, `--text`, `--muted`, `--border`, `--primary`, `--radius`, `--radius-sm`, `--shadow`, `--tabbar-h`)만 참조한다.** 색을 직접 쓰면 다크모드에서 깨진다.
- **폼에는 `novalidate` 를 붙인다.** 브라우저 기본 검증이 `submit` 을 막으면 서버가 준 에러 메시지를 시트에 띄울 기회가 사라진다. 예산 시트에서 이미 겪은 함정이다.
- **이름이 공백뿐이면 거부한다.** 예산과 다른 점이다 — 이름 없는 체크리스트 항목은 아무 뜻이 없다.
- **에러 규약:** 서비스가 `require(...)` 로 던진 `IllegalArgumentException` → 400, `NoSuchElementException` → 404. `config/GlobalExceptionHandler` 가 이미 둘 다 `{"error":"..."}` 로 바꿔준다. 컨트롤러에 예외 처리를 새로 쓰지 않는다.
- **DB 에서 온 문자열은 `innerHTML` 에 닿기 전에 반드시 escape.** `budget-list.js` 의 `escapeHtml` 이 집 안의 패턴이다.
- **테스트 실행:** `./gradlew test`. 단일 클래스는 `./gradlew test --tests "com.juiceplan.check.CheckServiceTest"`.
- **커밋 메시지는 한국어**, `feat:` / `fix:` 접두어. **`git add -A` 를 쓰지 말고 자기 파일만 명시적으로 스테이징한다.**
- 주석은 한국어로, *왜* 를 적는다.

---

## 파일 구조

### 새로 만드는 파일

| 파일 | 책임 |
|---|---|
| `src/main/kotlin/com/juiceplan/check/CheckItem.kt` | 엔티티 + `CheckList` enum |
| `src/main/kotlin/com/juiceplan/check/CheckItemRepository.kt` | 리포지토리 |
| `src/main/kotlin/com/juiceplan/check/CheckService.kt` | CRUD + 체크 토글 + 정렬 |
| `src/main/kotlin/com/juiceplan/check/CheckApiController.kt` | `/api/check/**` |
| `src/main/resources/static/js/check.js` | 섹션 내 라우팅 |
| `src/main/resources/static/js/check-list.js` | 세 탭이 공유하는 목록 렌더러 + 편집 시트 |

### 고치는 파일

| 파일 | 이유 |
|---|---|
| `src/main/kotlin/com/juiceplan/nav/Nav.kt` | check 탭 2개 추가 |
| `src/main/kotlin/com/juiceplan/check/CheckPageController.kt` | 서비스 주입, `CHECK_ITEMS` 인라인 |
| `src/main/resources/templates/check/index.html` | 스텁 → 뷰 컨테이너 셋 + 편집 시트 |
| `src/main/resources/static/js/api.js` | 체크 API 메서드 4개 |
| `src/main/resources/static/css/style.css` | 체크리스트 행·체크박스·입력줄 |
| `src/test/kotlin/com/juiceplan/nav/SectionNavigationIntegrationTest.kt` | 탭 셋 렌더 확인 |

---

## Task 1: 탭 셋과 페이지 골격

**Files:**
- Modify: `src/main/kotlin/com/juiceplan/nav/Nav.kt`
- Modify: `src/main/resources/templates/check/index.html`
- Create: `src/main/resources/static/js/check.js`
- Test: `src/test/kotlin/com/juiceplan/nav/SectionNavigationIntegrationTest.kt`

**Interfaces:**
- Consumes: `Nav`, `NavSection`, `Model.addNav`, `~{fragments/tabbar :: tabbar}`, CSS 클래스 `.section-body` / `.view` (전부 예산 작업에서 이미 있음)
- Produces: `#view-shopping` / `#view-packing` / `#view-todo` 엘리먼트, `check.js` 의 탭 라우팅

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`SectionNavigationIntegrationTest` 에 추가한다:

```kotlin
    @Test
    fun `every check tab is reachable`() {
        listOf("/check/shopping", "/check/packing", "/check/todo").forEach { path ->
            mockMvc.perform(get(path)).andExpect(status().isOk)
        }
    }

    @Test
    fun `the check page ships all three tab views so the client can swap them`() {
        mockMvc.perform(get("/check/todo"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("id=\"view-shopping\"")))
            .andExpect(content().string(containsString("id=\"view-packing\"")))
            .andExpect(content().string(containsString("id=\"view-todo\"")))
    }

    @Test
    fun `the check tab bar shows three tabs and no forward arrow`() {
        val html = mockMvc.perform(get("/check/shopping")).andReturn().response.contentAsString

        assertEquals(3, Regex("data-tab=").findAll(html).count())
        assertTrue(html.contains("nav-arrow--off"))
    }
```

`import org.junit.jupiter.api.Assertions.assertTrue` 를 함께 넣는다 (`assertEquals` 는 이미 있다).

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew test --tests "com.juiceplan.nav.SectionNavigationIntegrationTest"`
Expected: FAIL — `/check/packing` 과 `/check/todo` 가 기본 탭으로 리다이렉트(302)되고, `#view-*` 컨테이너가 없다

- [ ] **Step 3: 탭을 더한다**

`Nav.kt` 의 `check` 섹션을 바꾼다:

```kotlin
        NavSection(
            id = "check", defaultTab = "shopping",
            tabs = listOf(
                NavTab("shopping", "🛒", "쇼핑 목록"),
                NavTab("packing", "🎒", "준비물"),
                NavTab("todo", "✅", "할일"),
            )
        ),
```

- [ ] **Step 4: 페이지 골격을 만든다**

`src/main/resources/templates/check/index.html` 의 `<body>` 안을 바꾼다. 세 탭이 같은 렌더러를 쓰므로 컨테이너만 셋이고 안은 비어 있다 — 내용은 Task 4 가 채운다:

```html
<div class="section-body">
    <section id="view-shopping" class="view" hidden></section>
    <section id="view-packing" class="view" hidden></section>
    <section id="view-todo" class="view" hidden></section>
</div>

<footer th:replace="~{fragments/tabbar :: tabbar}"></footer>

<script th:src="@{/js/check.js}"></script>
```

`<head>` 는 그대로 둔다.

- [ ] **Step 5: 라우팅 JS 를 만든다**

`src/main/resources/static/js/check.js`:

```js
// 체크리스트 섹션 라우팅. budget.js 와 같은 방식으로 탭을 옮길 때 서버로 가지 않고
// 주소만 바꾼다. 세 탭이 같은 렌더러를 쓰므로 뷰 모듈은 하나뿐이고 탭 이름만 넘긴다.
(function () {
    const TABS = ['shopping', 'packing', 'todo'];
    const DEFAULT_TAB = 'shopping';
    const BASE = '/check';   // CheckPageController 의 @GetMapping("/check/{tab}") 과 같아야 한다

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

- [ ] **Step 6: 통과를 확인한다**

Run: `./gradlew test`
Expected: PASS — 새 테스트 3개와 기존 전부. `NavTest` 의 "기본 탭이 탭 목록 안에 있다" 도 그대로 통과해야 한다.

- [ ] **Step 7: 커밋**

```bash
git add src/main/kotlin/com/juiceplan/nav/Nav.kt \
        src/main/resources/templates/check/index.html \
        src/main/resources/static/js/check.js \
        src/test/kotlin/com/juiceplan/nav/SectionNavigationIntegrationTest.kt
git commit -m "feat: 체크리스트 섹션에 탭 셋을 놓는다"
```

---

## Task 2: 항목 모델과 서비스

**Files:**
- Create: `src/main/kotlin/com/juiceplan/check/CheckItem.kt`
- Create: `src/main/kotlin/com/juiceplan/check/CheckItemRepository.kt`
- Create: `src/main/kotlin/com/juiceplan/check/CheckService.kt`
- Test: `src/test/kotlin/com/juiceplan/check/CheckServiceTest.kt`

**Interfaces:**
- Consumes: 없음
- Produces:
  - `CheckList { SHOPPING, PACKING, TODO }`
  - `CheckItem(id, list, name, checked, memo)`
  - `CheckItemRepository : JpaRepository<CheckItem, Long>`
  - `CheckItemInput(list: CheckList, name: String, memo: String?)`
  - `CheckService.list(): List<CheckItem>` — 체크 안 한 것 먼저, 그 안에서 id 순
  - `CheckService.create(input): CheckItem`
  - `CheckService.update(id, name: String, memo: String?): CheckItem`
  - `CheckService.setChecked(id, checked: Boolean): CheckItem`
  - `CheckService.delete(id)`

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`src/test/kotlin/com/juiceplan/check/CheckServiceTest.kt`:

```kotlin
package com.juiceplan.check

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import

@DataJpaTest
@Import(CheckService::class)
class CheckServiceTest {

    @Autowired lateinit var checkService: CheckService
    @Autowired lateinit var repository: CheckItemRepository

    @BeforeEach
    fun clear() {
        repository.deleteAll()
    }

    private fun input(
        name: String = "시로이코이비토",
        list: CheckList = CheckList.SHOPPING,
        memo: String? = null
    ) = CheckItemInput(list = list, name = name, memo = memo)

    @Test
    fun `create stores the item unchecked and returns it with an id`() {
        val saved = checkService.create(input())

        assertTrue(saved.id > 0)
        assertFalse(saved.checked)
        assertEquals("시로이코이비토", repository.findById(saved.id).get().name)
    }

    @Test
    fun `create trims the name`() {
        assertEquals("여권", checkService.create(input(name = "  여권  ")).name)
    }

    @Test
    fun `create rejects a blank name`() {
        // 이름 없는 체크리스트 항목은 아무 뜻이 없다 (예산 항목과 다른 점)
        assertThrows(IllegalArgumentException::class.java) { checkService.create(input(name = "   ")) }
    }

    @Test
    fun `an empty memo is stored as null`() {
        assertNull(checkService.create(input(memo = "   ")).memo)
    }

    @Test
    fun `update changes the name and memo but not the checked state`() {
        val saved = checkService.create(input())
        checkService.setChecked(saved.id, true)

        val updated = checkService.update(saved.id, "로이스 생초콜릿", "냉장 보관")

        assertEquals("로이스 생초콜릿", updated.name)
        assertEquals("냉장 보관", updated.memo)
        assertTrue(updated.checked)
    }

    @Test
    fun `update rejects a blank name`() {
        val saved = checkService.create(input())

        assertThrows(IllegalArgumentException::class.java) { checkService.update(saved.id, " ", null) }
    }

    @Test
    fun `setChecked toggles both ways`() {
        val saved = checkService.create(input())

        assertTrue(checkService.setChecked(saved.id, true).checked)
        assertFalse(checkService.setChecked(saved.id, false).checked)
    }

    @Test
    fun `delete removes the item`() {
        val saved = checkService.create(input())

        checkService.delete(saved.id)

        assertTrue(repository.findById(saved.id).isEmpty)
    }

    @Test
    fun `an unknown id is rejected on every path that needs one`() {
        assertThrows(NoSuchElementException::class.java) { checkService.update(999, "x", null) }
        assertThrows(NoSuchElementException::class.java) { checkService.setChecked(999, true) }
        assertThrows(NoSuchElementException::class.java) { checkService.delete(999) }
    }

    @Test
    fun `list keeps unchecked items first and insertion order within each group`() {
        val first = checkService.create(input(name = "첫째"))
        val second = checkService.create(input(name = "둘째"))
        val third = checkService.create(input(name = "셋째"))

        checkService.setChecked(first.id, true)

        assertEquals(listOf("둘째", "셋째", "첫째"), checkService.list().map { it.name })
    }

    @Test
    fun `unchecking puts an item back where it was`() {
        val first = checkService.create(input(name = "첫째"))
        checkService.create(input(name = "둘째"))

        checkService.setChecked(first.id, true)
        checkService.setChecked(first.id, false)

        // 순서를 따로 저장하지 않으므로 id 순서가 곧 원래 자리다
        assertEquals(listOf("첫째", "둘째"), checkService.list().map { it.name })
    }

    @Test
    fun `list holds every list's items so the client can split them by tab`() {
        checkService.create(input(name = "시로이코이비토", list = CheckList.SHOPPING))
        checkService.create(input(name = "여권", list = CheckList.PACKING))
        checkService.create(input(name = "환전", list = CheckList.TODO))

        assertEquals(
            listOf(CheckList.SHOPPING, CheckList.PACKING, CheckList.TODO),
            checkService.list().map { it.list }
        )
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew test --tests "com.juiceplan.check.CheckServiceTest"`
Expected: FAIL — `Unresolved reference: CheckService`

- [ ] **Step 3: 엔티티와 리포지토리를 만든다**

`src/main/kotlin/com/juiceplan/check/CheckItem.kt`:

```kotlin
package com.juiceplan.check

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/** 어느 탭의 항목인가. 값을 늘리는 것이 곧 탭을 하나 더하는 일이다. */
enum class CheckList { SHOPPING, PACKING, TODO }

/**
 * 체크리스트 항목 하나. 세 목록이 전부 같은 모양(이름·체크·메모)이라 엔티티도 하나다.
 * 목록마다 엔티티를 나누면 같은 코드를 세 번 쓰게 되고, 목록을 더할 때마다 또 한 벌이 는다.
 *
 * enum 은 문자열로 저장한다. Hibernate 는 H2 에서 enum 필드를 ENUM('A','B') 네이티브 컬럼으로
 * 만드는데 ddl-auto: update 가 그 값 목록을 넓혀주지 않아, 목록을 하나 더하면 기존 DB 가
 * 새 값을 거부한다. Source.placeType, BudgetItem.category 와 같은 이유다.
 */
@Entity
class CheckItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    var list: CheckList,

    var name: String,

    var checked: Boolean = false,

    var memo: String? = null
)
```

`src/main/kotlin/com/juiceplan/check/CheckItemRepository.kt`:

```kotlin
package com.juiceplan.check

import org.springframework.data.jpa.repository.JpaRepository

interface CheckItemRepository : JpaRepository<CheckItem, Long>
```

- [ ] **Step 4: 서비스를 만든다**

`src/main/kotlin/com/juiceplan/check/CheckService.kt`:

```kotlin
package com.juiceplan.check

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 화면이 보내는 항목 값. 엔티티와 달리 id 가 없고 이름에 앞뒤 공백이 남아 있다. */
data class CheckItemInput(
    val list: CheckList,
    val name: String,
    val memo: String? = null
)

@Service
class CheckService(private val repository: CheckItemRepository) {

    /**
     * 체크 안 한 것이 먼저, 그 안에서는 id 순.
     *
     * 순서 컬럼을 두지 않으므로 넣은 순서대로 쌓이고, 체크를 풀면 원래 자리로 저절로 돌아온다.
     * 목록을 나누지 않고 전부 돌려주는 이유는 화면이 탭을 옮길 때 서버로 다시 오지 않기 때문이다.
     */
    fun list(): List<CheckItem> =
        repository.findAll().sortedWith(compareBy({ it.checked }, { it.id }))

    @Transactional
    fun create(input: CheckItemInput): CheckItem =
        repository.save(
            CheckItem(
                list = input.list,
                name = cleanName(input.name),
                memo = cleanMemo(input.memo)
            )
        )

    @Transactional
    fun update(id: Long, name: String, memo: String?): CheckItem {
        val item = find(id)
        item.name = cleanName(name)
        item.memo = cleanMemo(memo)
        return repository.save(item)
    }

    /** 체크는 이 화면에서 가장 잦은 동작이라 이름·메모를 건드리지 않는 전용 통로를 둔다. */
    @Transactional
    fun setChecked(id: Long, checked: Boolean): CheckItem {
        val item = find(id)
        item.checked = checked
        return repository.save(item)
    }

    @Transactional
    fun delete(id: Long) {
        repository.delete(find(id))
    }

    private fun find(id: Long): CheckItem =
        repository.findById(id).orElseThrow { NoSuchElementException("없는 체크 항목입니다.") }
}

/** 이름 없는 체크 항목은 아무 뜻이 없다. 예산 항목이 빈 이름을 허용하는 것과 다르다. */
private fun cleanName(name: String): String {
    val trimmed = name.trim()
    require(trimmed.isNotEmpty()) { "이름을 입력해주세요." }
    return trimmed
}

private fun cleanMemo(memo: String?): String? = memo?.trim()?.ifEmpty { null }
```

- [ ] **Step 5: 통과를 확인한다**

Run: `./gradlew test --tests "com.juiceplan.check.CheckServiceTest"`
Expected: PASS (12개 테스트)

- [ ] **Step 6: 커밋**

```bash
git add src/main/kotlin/com/juiceplan/check/CheckItem.kt \
        src/main/kotlin/com/juiceplan/check/CheckItemRepository.kt \
        src/main/kotlin/com/juiceplan/check/CheckService.kt \
        src/test/kotlin/com/juiceplan/check/CheckServiceTest.kt
git commit -m "feat: 체크리스트 항목 모델과 서비스"
```

---

## Task 3: API 와 페이지 데이터

**Files:**
- Create: `src/main/kotlin/com/juiceplan/check/CheckApiController.kt`
- Modify: `src/main/kotlin/com/juiceplan/check/CheckPageController.kt`
- Modify: `src/main/resources/templates/check/index.html`
- Modify: `src/main/resources/static/js/api.js`
- Test: `src/test/kotlin/com/juiceplan/check/CheckApiIntegrationTest.kt`

**Interfaces:**
- Consumes: Task 2 전부
- Produces:
  - `POST /api/check/items`, `PUT /api/check/items/{id}`, `PUT /api/check/items/{id}/checked`, `DELETE /api/check/items/{id}`
  - 인라인 전역 `window.CHECK_ITEMS`
  - `window.Api.createCheckItem(payload)`, `updateCheckItem(id, payload)`, `setCheckItemChecked(id, checked)`, `deleteCheckItem(id)`

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`src/test/kotlin/com/juiceplan/check/CheckApiIntegrationTest.kt`:

```kotlin
package com.juiceplan.check

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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.hamcrest.Matchers.containsString

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CheckApiIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var repository: CheckItemRepository
    @Autowired lateinit var checkService: CheckService

    private val body = """{"list":"PACKING","name":"여권","memo":"유효기간 확인"}"""

    @BeforeEach
    fun clear() {
        repository.deleteAll()
    }

    @Test
    fun `create returns the saved item with its id, unchecked`() {
        mockMvc.perform(post("/api/check/items").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").isNumber)
            .andExpect(jsonPath("$.list").value("PACKING"))
            .andExpect(jsonPath("$.checked").value(false))
    }

    @Test
    fun `a blank name is a 400 with a message`() {
        val bad = """{"list":"PACKING","name":"   ","memo":null}"""

        mockMvc.perform(post("/api/check/items").contentType(MediaType.APPLICATION_JSON).content(bad))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("이름을 입력해주세요."))
    }

    @Test
    fun `update changes the name and memo`() {
        val id = checkService.create(CheckItemInput(CheckList.TODO, "환전")).id

        mockMvc.perform(
            put("/api/check/items/$id").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"공항에서 환전","memo":"수수료 확인"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("공항에서 환전"))
            .andExpect(jsonPath("$.memo").value("수수료 확인"))
    }

    @Test
    fun `checked can be toggled without touching the name`() {
        val id = checkService.create(CheckItemInput(CheckList.SHOPPING, "로이스")).id

        mockMvc.perform(
            put("/api/check/items/$id/checked").contentType(MediaType.APPLICATION_JSON)
                .content("""{"checked":true}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.checked").value(true))
            .andExpect(jsonPath("$.name").value("로이스"))
    }

    @Test
    fun `delete removes the item`() {
        val id = checkService.create(CheckItemInput(CheckList.SHOPPING, "지울 것")).id

        mockMvc.perform(delete("/api/check/items/$id")).andExpect(status().isOk)

        mockMvc.perform(get("/check/shopping"))
            .andExpect(content().string(containsString("var CHECK_ITEMS = []")))
    }

    @Test
    fun `an unknown id is a 404 on every path`() {
        mockMvc.perform(
            put("/api/check/items/999").contentType(MediaType.APPLICATION_JSON).content("""{"name":"x","memo":null}""")
        ).andExpect(status().isNotFound)
        mockMvc.perform(
            put("/api/check/items/999/checked").contentType(MediaType.APPLICATION_JSON).content("""{"checked":true}""")
        ).andExpect(status().isNotFound)
        mockMvc.perform(delete("/api/check/items/999")).andExpect(status().isNotFound)
    }

    @Test
    fun `the check page embeds its items for the client`() {
        checkService.create(CheckItemInput(CheckList.PACKING, "여권"))

        // Thymeleaf 의 JS 인라이닝은 한글을 \uXXXX 로 이스케이프하므로 ASCII 로 남는 필드만 본다
        mockMvc.perform(get("/check/packing"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("var CHECK_ITEMS =")))
            .andExpect(content().string(containsString("\"list\":\"PACKING\"")))
            .andExpect(content().string(containsString("\"checked\":false")))
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

Run: `./gradlew test --tests "com.juiceplan.check.CheckApiIntegrationTest"`
Expected: FAIL — 모든 `/api/check/**` 경로가 404

- [ ] **Step 3: API 컨트롤러를 만든다**

`src/main/kotlin/com/juiceplan/check/CheckApiController.kt`:

```kotlin
package com.juiceplan.check

import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

data class CheckItemRequest(val list: CheckList, val name: String, val memo: String? = null) {
    fun toInput() = CheckItemInput(list, name, memo)
}

/** 수정은 목록을 옮기지 않는다. 쇼핑에 적은 것을 준비물로 보내는 일은 만들지 않았다. */
data class CheckItemEdit(val name: String, val memo: String? = null)

data class CheckedRequest(val checked: Boolean)

/**
 * 저장·수정이 엔티티를 그대로 돌려주는 이유는 SourceController·BudgetApiController 와 같다 —
 * 클라이언트가 생성된 id 를 알아야 목록에 넣고 이후 수정·삭제를 걸 수 있다.
 *
 * 체크 토글에 전용 경로를 두는 이유는 이게 이 화면에서 압도적으로 잦은 동작이기 때문이다.
 * 체크 한 번에 이름과 메모까지 통째로 보내면, 시트를 열지도 않은 채 이름을 덮어쓰는 셈이 된다.
 */
@RestController
class CheckApiController(private val checkService: CheckService) {

    @PostMapping("/api/check/items")
    fun create(@RequestBody request: CheckItemRequest): CheckItem =
        checkService.create(request.toInput())

    @PutMapping("/api/check/items/{id}")
    fun update(@PathVariable id: Long, @RequestBody request: CheckItemEdit): CheckItem =
        checkService.update(id, request.name, request.memo)

    @PutMapping("/api/check/items/{id}/checked")
    fun setChecked(@PathVariable id: Long, @RequestBody request: CheckedRequest): CheckItem =
        checkService.setChecked(id, request.checked)

    @DeleteMapping("/api/check/items/{id}")
    fun delete(@PathVariable id: Long) {
        checkService.delete(id)
    }
}
```

- [ ] **Step 4: 페이지가 항목을 싣게 한다**

`CheckPageController` 를 바꾼다 (클래스 주석도 스텁 시절 문구를 걷어낸다):

```kotlin
private const val SECTION = "check"

/**
 * 체크리스트 페이지. 세 탭이 같은 페이지를 쓰고 클라이언트가 갈아끼운다.
 * 세 목록의 항목을 한 번에 실어 보내므로 탭을 옮길 때 서버로 오지 않는다.
 */
@Controller
class CheckPageController(private val checkService: CheckService) {

    @GetMapping("/check")
    fun root(): RedirectView = RedirectView(Nav.section(SECTION).defaultPath())

    @GetMapping("/check/{tab}")
    fun page(@PathVariable tab: String, model: Model): Any {
        val section = Nav.section(SECTION)
        if (!section.has(tab)) return RedirectView(section.defaultPath())

        model.addAttribute("items", checkService.list())
        model.addNav(SECTION, tab)
        return "check/index"
    }
}
```

`check/index.html` 의 `<footer>` 아래에 더한다:

```html
<script th:inline="javascript">
    /*<![CDATA[*/
    var CHECK_ITEMS = /*[[${items}]]*/ [];
    /*]]>*/
</script>

<script th:src="@{/js/api.js}"></script>
<script th:src="@{/js/check.js}"></script>
```

기존의 `<script th:src="@{/js/check.js}"></script>` 한 줄은 지운다 — 위 블록이 대신한다.

- [ ] **Step 5: API 메서드를 더한다**

`api.js` 의 `return {` 블록 안, `deleteBudgetItem` 줄 아래에 더한다:

```js
        createCheckItem: (payload) => send('POST', '/api/check/items', payload),
        updateCheckItem: (id, payload) => send('PUT', `/api/check/items/${id}`, payload),
        setCheckItemChecked: (id, checked) => send('PUT', `/api/check/items/${id}/checked`, { checked }),
        deleteCheckItem: (id) => send('DELETE', `/api/check/items/${id}`),
```

- [ ] **Step 6: 통과를 확인한다**

Run: `./gradlew test`
Expected: PASS (전부)

- [ ] **Step 7: 커밋**

```bash
git add src/main/kotlin/com/juiceplan/check/ \
        src/main/resources/templates/check/index.html \
        src/main/resources/static/js/api.js \
        src/test/kotlin/com/juiceplan/check/CheckApiIntegrationTest.kt
git commit -m "feat: 체크리스트 API 와 페이지 데이터"
```

---

## Task 4: 목록 화면 — 렌더 · 빠른 추가 · 체크

**Files:**
- Create: `src/main/resources/static/js/check-list.js`
- Modify: `src/main/resources/static/js/check.js`
- Modify: `src/main/resources/templates/check/index.html`
- Modify: `src/main/resources/static/css/style.css`

**Interfaces:**
- Consumes: `window.CHECK_ITEMS`, `window.Api.createCheckItem` / `setCheckItemChecked` (Task 3), `#view-shopping` / `#view-packing` / `#view-todo` (Task 1)
- Produces:
  - `window.ViewCheck` — `init()`, `show(tab)`
  - `window.CheckSection.refresh()` — 지금 탭을 다시 그린다
  - CSS 클래스 `.check-add`, `.check-progress`, `.check-list`, `.check-row`, `.check-row--done`, `.check-row__box`, `.check-row__body`

- [ ] **Step 1: 목록 렌더러를 만든다**

`src/main/resources/static/js/check-list.js`:

```js
// 체크리스트 목록. 세 탭이 이 렌더러 하나를 공유하고 어느 목록인지만 다르다.
window.ViewCheck = (function () {
    // 탭 이름 → 서버의 CheckList 값
    const LISTS = { shopping: 'SHOPPING', packing: 'PACKING', todo: 'TODO' };

    const PLACEHOLDERS = {
        shopping: '예: 시로이코이비토',
        packing: '예: 여권, 충전기',
        todo: '예: 출국 전 환전',
    };

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    /** 서버와 같은 순서: 체크 안 한 것이 먼저, 그 안에서는 넣은 순서(id). */
    function sortItems() {
        window.CHECK_ITEMS.sort((a, b) =>
            (a.checked === b.checked ? a.id - b.id : (a.checked ? 1 : -1)));
    }

    function itemsOf(tab) {
        return window.CHECK_ITEMS.filter((i) => i.list === LISTS[tab]);
    }

    function rowHtml(item) {
        return `<li class="check-row${item.checked ? ' check-row--done' : ''}" data-id="${item.id}">` +
            `<input type="checkbox" class="check-row__box"${item.checked ? ' checked' : ''} aria-label="완료">` +
            // 이름을 버튼으로 감싸야 키보드로도 편집 시트를 열 수 있다
            '<button type="button" class="check-row__body">' +
                `<span class="check-row__name">${escapeHtml(item.name)}</span>` +
                (item.memo ? `<span class="check-row__memo muted">${escapeHtml(item.memo)}</span>` : '') +
            '</button>' +
            '</li>';
    }

    function show(tab) {
        const items = itemsOf(tab);
        const done = items.filter((i) => i.checked).length;

        document.getElementById('view-' + tab).innerHTML =
            // 체크리스트는 한 번에 여러 개를 몰아 넣는 화면이라 시트보다 한 줄 입력이 빠르다.
            // form 으로 감싸면 엔터가 그대로 제출이 된다.
            `<form class="check-add" data-tab="${tab}" novalidate>` +
                `<input type="text" class="check-add__input" placeholder="${PLACEHOLDERS[tab]}" aria-label="항목 추가">` +
                '<button type="submit" class="btn check-add__btn">추가</button>' +
            '</form>' +
            (items.length === 0
                ? '<p class="card muted">아직 항목이 없습니다.</p>'
                : `<p class="muted check-progress">${done}/${items.length} 완료</p>` +
                  `<ul class="check-list">${items.map(rowHtml).join('')}</ul>`);
    }

    /** 목록을 다시 그린 뒤 입력칸으로 돌아온다. 연달아 적을 때 손이 멈추지 않게 한다. */
    function focusInput(tab) {
        const input = document.querySelector(`#view-${tab} .check-add__input`);
        if (input) input.focus();
    }

    async function add(form) {
        const input = form.querySelector('.check-add__input');
        const name = input.value.trim();
        if (!name) return;

        const tab = form.dataset.tab;
        try {
            window.CHECK_ITEMS.push(await window.Api.createCheckItem({
                list: LISTS[tab], name, memo: null,
            }));
            sortItems();
            window.CheckSection.refresh();
            focusInput(tab);
        } catch (e) {
            alert(e.message);
        }
    }

    async function toggle(box) {
        const row = box.closest('.check-row');
        try {
            const updated = await window.Api.setCheckItemChecked(Number(row.dataset.id), box.checked);
            const i = window.CHECK_ITEMS.findIndex((it) => it.id === updated.id);
            window.CHECK_ITEMS[i] = updated;
            sortItems();
            window.CheckSection.refresh();
        } catch (e) {
            // 서버가 거절했는데 체크된 것처럼 보이면 안 된다
            box.checked = !box.checked;
            alert(e.message);
        }
    }

    function init() {
        // 목록은 매번 새로 그려지므로 이벤트는 바깥 상자에 한 번만 건다
        const host = document.querySelector('.section-body');

        host.addEventListener('submit', (e) => {
            if (!e.target.classList.contains('check-add')) return;
            e.preventDefault();
            add(e.target);
        });

        host.addEventListener('change', (e) => {
            if (e.target.classList.contains('check-row__box')) toggle(e.target);
        });
    }

    return { init, show };
})();
```

- [ ] **Step 2: 라우터가 목록을 그리게 한다**

`check.js` 의 `show(tab)` 마지막 줄(`current = tab;`) 뒤에 더한다:

```js
        window.ViewCheck.show(tab);
```

파일 끝의 `route();` 바로 앞에 더한다:

```js
    // 데이터가 바뀌었을 때 지금 탭을 다시 그리게 하는 통로
    window.CheckSection = {
        refresh: () => { if (current) window.ViewCheck.show(current); },
        currentTab: () => current,
    };

    window.ViewCheck.init();
```

- [ ] **Step 3: 스크립트를 싣는다**

`check/index.html` 의 `api.js` 줄과 `check.js` 줄 사이에 넣는다 (`check.js` 가 IIFE 실행 시점에 `window.ViewCheck` 를 읽으므로 반드시 그 앞이어야 한다):

```html
<script th:src="@{/js/check-list.js}"></script>
```

- [ ] **Step 4: CSS 를 더한다**

`style.css` 의 예산 블록 끝에 붙인다:

```css
/* ============================================================
   체크리스트
   ============================================================ */
.check-add { display: flex; gap: 8px; padding: 8px 16px 0; }
.check-add__input { flex: 1 1 auto; }
/* 버튼이 늘어나면 입력칸이 좁아진다. 글자 폭만 차지하게 둔다. */
.check-add__btn { flex: 0 0 auto; }

.check-progress { margin: 10px 16px 2px; }

.check-list { list-style: none; margin: 0; padding: 0 8px 8px; }

.check-row {
  display: flex; align-items: flex-start; gap: 10px;
  background: var(--surface);
  border-radius: var(--radius-sm);
  box-shadow: var(--shadow);
  margin: 6px 0; padding: 10px 12px;
}
/* input 전역 규칙이 width:100%, min-height:44px 라 체크박스가 한 줄을 통째로 먹는다 */
.check-row__box {
  flex: 0 0 auto;
  width: 22px; height: 22px; min-height: 0;
  margin-top: 2px;
}
/* 이름 전체가 편집 시트를 여는 버튼이지만 버튼처럼 보이면 안 된다 */
.check-row__body {
  flex: 1 1 auto; min-width: 0;
  display: flex; flex-direction: column; gap: 2px;
  background: none; border: 0; padding: 0;
  font: inherit; color: inherit; text-align: left; cursor: pointer;
}
.check-row__memo { white-space: pre-wrap; }

.check-row--done { opacity: .6; }
.check-row--done .check-row__name { text-decoration: line-through; color: var(--muted); }
```

- [ ] **Step 5: 테스트를 돌린다**

Run: `./gradlew test`
Expected: PASS — 이 태스크는 테스트가 닿지 않지만 기존 테스트가 깨지면 안 된다

- [ ] **Step 6: 눈으로 확인한다**

Run: `./gradlew bootRun`

`http://localhost:8080/check` 를 연다.

1. 쇼핑 목록 탭이 열리고 맨 위에 입력 한 줄, 아래에 "아직 항목이 없습니다"
2. `시로이코이비토` 를 치고 엔터 → 항목이 생기고 입력칸이 비면서 **포커스가 남아** 바로 다음 항목을 칠 수 있다
3. 두 개 더 넣고 첫 번째를 체크 → 취소선이 그어지고 흐려지며 목록 맨 아래로 내려간다. 위의 `1/3 완료` 가 바뀐다
4. 체크를 풀면 원래 자리(맨 위)로 돌아온다
5. 준비물·할일 탭으로 옮기면 각자 빈 목록이고, 입력 힌트가 탭마다 다르다
6. 다크모드에서 카드와 취소선이 모두 읽힌다

- [ ] **Step 7: 커밋**

```bash
git add src/main/resources/static/js/check-list.js \
        src/main/resources/static/js/check.js \
        src/main/resources/templates/check/index.html \
        src/main/resources/static/css/style.css
git commit -m "feat: 체크리스트 목록과 빠른 추가"
```

---

## Task 5: 편집 시트

**Files:**
- Modify: `src/main/resources/templates/check/index.html`
- Modify: `src/main/resources/static/js/check-list.js`

**Interfaces:**
- Consumes: `window.Api.updateCheckItem` / `deleteCheckItem` (Task 3), `window.CheckSection.refresh()` (Task 4), `.sheet` / `.sheet__backdrop` CSS (예산 작업에서 이미 있음)
- Produces: `#checkSheet`, `#checkBackdrop`, `#checkForm` 엘리먼트

- [ ] **Step 1: 시트 마크업을 더한다**

`check/index.html` 의 `</div>`(section-body) 와 `<footer>` 사이에 넣는다:

```html
<!-- ── 항목 수정 시트 ── -->
<div class="sheet__backdrop" id="checkBackdrop"></div>
<div class="sheet" id="checkSheet">
    <h3>항목 수정</h3>

    <!-- 이름 규칙은 서버가 갖는다. 브라우저 기본 검증을 켜두면 빈 이름으로 저장했을 때
         submit 자체가 막혀 서버의 400 메시지를 시트에 띄울 기회가 없어진다. -->
    <form id="checkForm" novalidate>
        <label for="checkName">이름</label>
        <input type="text" id="checkName" name="name">

        <label for="checkMemo">메모</label>
        <textarea id="checkMemo" name="memo" rows="2" placeholder="예: 회사 사람들 것까지"></textarea>

        <p id="checkError" class="error" hidden style="margin:8px 0 0;"></p>

        <button type="submit" class="btn btn--primary btn--block" style="margin-top:16px;">저장</button>
        <button type="button" class="btn btn--danger btn--block" id="checkDelete" style="margin-top:8px;">삭제</button>
        <button type="button" class="btn btn--ghost btn--block" id="checkSheetClose" style="margin-top:8px;">닫기</button>
    </form>
</div>
```

- [ ] **Step 2: 시트 동작을 더한다**

`check-list.js` 의 `toggle` 아래, `init` 위에 더한다:

```js
    let editingId = null;

    const sheet = () => document.getElementById('checkSheet');
    const backdrop = () => document.getElementById('checkBackdrop');

    function openSheet(item) {
        editingId = item.id;
        document.getElementById('checkName').value = item.name;
        document.getElementById('checkMemo').value = item.memo || '';
        document.getElementById('checkError').hidden = true;

        sheet().classList.add('sheet--open');
        backdrop().classList.add('sheet--open');
    }

    function closeSheet() {
        sheet().classList.remove('sheet--open');
        backdrop().classList.remove('sheet--open');
        editingId = null;
    }

    async function submit(e) {
        e.preventDefault();
        const errorEl = document.getElementById('checkError');
        errorEl.hidden = true;

        try {
            const updated = await window.Api.updateCheckItem(editingId, {
                name: document.getElementById('checkName').value,
                memo: document.getElementById('checkMemo').value || null,
            });
            const i = window.CHECK_ITEMS.findIndex((it) => it.id === updated.id);
            window.CHECK_ITEMS[i] = updated;
            closeSheet();
            window.CheckSection.refresh();
        } catch (err) {
            errorEl.textContent = err.message;
            errorEl.hidden = false;
        }
    }

    async function remove() {
        const item = window.CHECK_ITEMS.find((it) => it.id === editingId);
        if (!confirm(`'${item.name}'을(를) 삭제하시겠습니까?`)) return;

        try {
            await window.Api.deleteCheckItem(editingId);
            window.CHECK_ITEMS = window.CHECK_ITEMS.filter((it) => it.id !== item.id);
            closeSheet();
            window.CheckSection.refresh();
        } catch (err) {
            alert(err.message);
        }
    }
```

`init()` 의 `change` 리스너 아래에 더한다:

```js
        host.addEventListener('click', (e) => {
            const body = e.target.closest('.check-row__body');
            if (!body) return;
            const id = Number(body.closest('.check-row').dataset.id);
            openSheet(window.CHECK_ITEMS.find((it) => it.id === id));
        });

        document.getElementById('checkForm').addEventListener('submit', submit);
        document.getElementById('checkDelete').addEventListener('click', remove);
        document.getElementById('checkSheetClose').addEventListener('click', closeSheet);
        document.getElementById('checkBackdrop').addEventListener('click', closeSheet);
```

- [ ] **Step 3: 테스트를 돌린다**

Run: `./gradlew test`
Expected: PASS

- [ ] **Step 4: 눈으로 확인한다**

Run: `./gradlew bootRun`

`http://localhost:8080/check/shopping`:

1. 항목 이름을 누르면 시트가 올라오고 이름·메모가 채워져 있다
2. 메모를 적고 저장하면 목록의 이름 아래에 회색으로 붙는다
3. 이름을 지우고 저장하면 시트 안에 "이름을 입력해주세요." 가 뜨고 **시트가 닫히지 않는다**
4. 삭제를 누르면 확인 창이 뜨고, 지우면 목록에서 사라진다
5. 백드롭을 누르면 시트가 닫힌다
6. 체크한 항목의 이름을 눌러도 시트가 열린다 (체크박스와 이름의 터치 영역이 겹치지 않는다)

- [ ] **Step 5: 커밋**

```bash
git add src/main/resources/templates/check/index.html \
        src/main/resources/static/js/check-list.js
git commit -m "feat: 체크리스트 항목 수정·삭제"
```

---

## Self-Review 기록

**스펙 커버리지**

| 스펙 절 | 태스크 |
|---|---|
| 1. 라우팅 (탭 셋, 섹션 내 pushState) | Task 1 |
| 2. 데이터 모델 | Task 2 |
| 3. 정렬 규칙 | Task 2 (서버), Task 4 (클라이언트 `sortItems`) |
| 4. API + 검증 | Task 3 |
| 5. 화면 (입력줄, 체크, 진행, 시트) | Task 4, 5 |
| 6. 파일 목록 | 위 "파일 구조" |
| 7. 테스트 | Task 1·2·3 의 테스트 단계 |
| 8. 위험 (탭 셋이 된 탭바) | Task 1 의 `data-tab` 3개 테스트 |

**타입 일관성 확인**

- `CheckItemInput(list, name, memo)` — Task 2 정의, Task 3 의 `CheckItemRequest.toInput()` 과 테스트가 같은 순서로 쓴다
- `CheckService.update(id, name, memo)` 는 목록을 바꾸지 않는다 — `CheckItemEdit` 에 `list` 가 없는 것과 짝이 맞는다
- `window.CheckSection.refresh()` 는 Task 4 가 만들고 Task 4·5 가 쓴다
- `LISTS` 의 값(`SHOPPING`/`PACKING`/`TODO`)이 `CheckList` enum 이름과 같아야 한다 — 서버가 JSON 으로 그 이름을 그대로 낸다

**정렬 규칙이 두 곳에 있다**

서버 `CheckService.list()` 와 클라이언트 `sortItems()` 가 같은 규칙을 갖는다. 예산에서 합계 계산을 서버로 몰았던 것과 달리 여기서는 감수한다 — 규칙이 "체크 안 한 것 먼저, 그 안에서 id 순" 한 줄이고, 항목을 하나 고칠 때마다 전체 목록을 다시 받아오는 편이 더 비싸다. 서버 쪽은 `CheckServiceTest` 가 지키고, 클라이언트 쪽은 화면이 바로 드러낸다.
