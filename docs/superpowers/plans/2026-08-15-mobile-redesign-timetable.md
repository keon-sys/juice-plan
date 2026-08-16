# 모바일 리디자인 + 시각 기반 타임테이블 배정 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 소스를 04:00~28:00 타임테이블의 원하는 시각에 드래그해서 배정할 수 있게 하고, 앱 전체에 모바일 우선 디자인 시스템을 입힌다.

**Architecture:** `Source.sortOrder`(순서)를 `Source.startMinutes`(자정 기준 분, 240~1650)로 대체한다. 배정 API를 리스트 단위에서 단건 `PUT`으로 바꾼다. 프론트는 HTML5 Drag and Drop 대신 Pointer Events로 드래그를 직접 구현한다(모바일에서 HTML5 DnD가 동작하지 않기 때문). CSS는 외부 프레임워크 없이 CSS 변수 기반 디자인 토큰으로 새로 작성한다.

**Tech Stack:** Kotlin, Spring Boot (MVC + Data JPA), Thymeleaf, H2 file DB, 순수 CSS/JS, Google Maps JS API, JUnit 5 + MockMvc

**Spec:** `docs/superpowers/specs/2026-08-15-mobile-redesign-timetable-design.md`

## Global Constraints

- 외부 CSS/JS 프레임워크·CDN을 새로 추가하지 않는다. 유일한 예외는 이미 쓰고 있는 Google Maps JS API다.
- 웹폰트를 받지 않는다. 폰트는 `-apple-system, "Segoe UI", "Noto Sans KR", sans-serif`.
- 색상·간격·모서리는 전부 CSS 변수(디자인 토큰)로만 지정한다. 하드코딩한 hex 값을 컴포넌트 CSS에 쓰지 않는다.
- 다크모드는 `@media (prefers-color-scheme: dark)`에서 **토큰 값만** 재정의한다. 레이아웃 CSS를 복제하지 않는다.
- 색만으로 정보를 전달하지 않는다. 음식점/관광지는 항상 색 + 아이콘(🍴/📍)을 함께 쓴다.
- 터치 대상은 최소 44×44px.
- 타임테이블: 그리드는 04:00~28:00, 30분 = 1슬롯, 슬롯 높이 **28px** (총 48슬롯). **시작 시각 범위는 `[240, 1650]`** (04:00~27:30), 30의 배수. 28:00(1680)은 그리드의 아래 경계일 뿐 배정 가능한 시각이 아니다.
- 불변식: `Source.scheduledDate`와 `Source.startMinutes`는 항상 둘 다 null이거나 둘 다 값이 있다.
- 테스트 실행: `./gradlew test`
- 커밋 메시지는 한국어 본문 + Conventional Commits 접두사(`feat:`, `fix:`, `refactor:`, `test:`, `style:`).

---

## File Structure

**신규**

| 파일 | 책임 |
|---|---|
| `src/main/kotlin/com/juiceplan/schedule/ScheduleTimeMigration.kt` | 기존 `sort_order` → `start_minutes` 1회성 멱등 마이그레이션 |
| `src/main/kotlin/com/juiceplan/dayview/DayViewController.kt` | `/day` 읽기 전용 화면 |
| `src/main/resources/templates/dayview/index.html` | `/day` 마크업 |
| `src/main/resources/static/js/timegrid.js` | 타임테이블 순수 계산 함수 (`snapToSlot`, `layoutBlocks`, `formatSlot`) — plan/dayview 공용 |
| `src/main/resources/static/js/dragdrop.js` | Pointer Events 드래그 엔진 (DOM 조작만, 도메인 로직 없음) |
| `src/main/resources/static/js/dayview.js` | `/day` 화면 렌더링 |
| `src/test/kotlin/com/juiceplan/schedule/ScheduleTimeMigrationTest.kt` | 마이그레이션 테스트 |
| `src/test/kotlin/com/juiceplan/dayview/DayViewControllerIntegrationTest.kt` | `/day` 인증·렌더링 테스트 |

**수정**

| 파일 | 변경 |
|---|---|
| `source/Source.kt` | `sortOrder` 제거, `startMinutes` 추가 |
| `schedule/ScheduleService.kt` | `assignDay` → `assign(sourceId, date, startMinutes)` |
| `schedule/ScheduleController.kt` | `POST /api/schedule/day/{date}` → `PUT /api/schedule/{sourceId}` |
| `config/WebConfig.kt` | 인터셉터 경로에 `/day` 추가 |
| `static/css/style.css` | 전면 재작성 |
| `static/js/plan.js` | 전면 재작성 |
| `static/js/sources.js` | 시트 UI에 맞게 수정 |
| `templates/fragments/layout.html` | 탭 3개 + 공용 `head` fragment |
| `templates/plan/index.html`, `templates/sources/index.html` | 재작성 |
| `templates/auth/login.html`, `templates/auth/setup.html` | 새 디자인 적용 |

**분리 이유:** `timegrid.js`(순수 계산)와 `dragdrop.js`(제스처)를 `plan.js`(화면 조립)에서 떼어낸다. 계산 함수는 콘솔에서 직접 호출해 검증할 수 있고, 드래그 엔진은 `/day` 화면이 재사용하지 않지만 `plan.js`가 400줄 넘게 부풀지 않게 한다.

---

## Task 1: Source 모델을 시각 기반으로 전환

`sortOrder`를 `startMinutes`로 바꾼다. 이 태스크만으로는 기존 `ScheduleService`가 깨지므로 서비스도 같은 태스크에서 고친다.

**Files:**
- Modify: `src/main/kotlin/com/juiceplan/source/Source.kt`
- Modify: `src/main/kotlin/com/juiceplan/schedule/ScheduleService.kt`
- Test: `src/test/kotlin/com/juiceplan/schedule/ScheduleServiceTest.kt`

**Interfaces:**
- Consumes: 없음 (첫 태스크)
- Produces:
  - `Source.startMinutes: Int?` — 자정 기준 분, 240~1650
  - `ScheduleService.assign(sourceId: Long, date: LocalDate, startMinutes: Int)`
  - `ScheduleService.remove(sourceId: Long)`
  - 상수 `DAY_START_MINUTES = 240`, `DAY_END_MINUTES = 1680`, `SLOT_MINUTES = 30`, `LAST_START_MINUTES = 1650` (모두 `ScheduleService.kt` 파일 최상단 `const val`)

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`src/test/kotlin/com/juiceplan/schedule/ScheduleServiceTest.kt` 전체를 아래로 교체한다.

```kotlin
package com.juiceplan.schedule

import com.juiceplan.source.PlaceType
import com.juiceplan.source.Source
import com.juiceplan.source.SourceRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import java.time.LocalDate

@DataJpaTest
@Import(ScheduleService::class)
class ScheduleServiceTest {

    @Autowired lateinit var sourceRepository: SourceRepository
    @Autowired lateinit var scheduleService: ScheduleService

    private val date = LocalDate.of(2026, 9, 1)

    private fun newSource(name: String) = sourceRepository.save(
        Source(
            googleMapsUrl = "https://maps.app.goo.gl/x",
            name = name,
            latitude = 37.0,
            longitude = 127.0,
            placeType = PlaceType.ATTRACTION,
            durationMinutes = 60,
            reservationRequired = false
        )
    )

    @Test
    fun `assign sets scheduledDate and startMinutes together`() {
        val a = newSource("A")

        scheduleService.assign(a.id, date, 600)

        val reloaded = sourceRepository.findById(a.id).get()
        assertEquals(date, reloaded.scheduledDate)
        assertEquals(600, reloaded.startMinutes)
    }

    @Test
    fun `assign moves an already scheduled source to another date and time`() {
        val a = newSource("A")
        scheduleService.assign(a.id, date, 600)

        scheduleService.assign(a.id, date.plusDays(1), 900)

        val reloaded = sourceRepository.findById(a.id).get()
        assertEquals(date.plusDays(1), reloaded.scheduledDate)
        assertEquals(900, reloaded.startMinutes)
    }

    @Test
    fun `remove clears scheduledDate and startMinutes together`() {
        val a = newSource("A")
        scheduleService.assign(a.id, date, 600)

        scheduleService.remove(a.id)

        val reloaded = sourceRepository.findById(a.id).get()
        assertNull(reloaded.scheduledDate)
        assertNull(reloaded.startMinutes)
    }

    @Test
    fun `assign accepts the boundary slots 0400 and 2730`() {
        val a = newSource("A")
        val b = newSource("B")

        scheduleService.assign(a.id, date, 240)
        scheduleService.assign(b.id, date, 1650)

        assertEquals(240, sourceRepository.findById(a.id).get().startMinutes)
        assertEquals(1650, sourceRepository.findById(b.id).get().startMinutes)
    }

    @Test
    fun `assign rejects a time before 0400`() {
        val a = newSource("A")

        val ex = assertThrows(IllegalArgumentException::class.java) {
            scheduleService.assign(a.id, date, 210)
        }
        assertEquals("시간은 04:00~27:30 사이여야 합니다.", ex.message)
    }

    @Test
    fun `assign rejects 2800 because it is the grid edge, not a placeable slot`() {
        val a = newSource("A")

        assertThrows(IllegalArgumentException::class.java) {
            scheduleService.assign(a.id, date, 1680)
        }
    }

    @Test
    fun `assign rejects a time that is not on a 30 minute slot`() {
        val a = newSource("A")

        val ex = assertThrows(IllegalArgumentException::class.java) {
            scheduleService.assign(a.id, date, 615)
        }
        assertEquals("시간은 30분 단위여야 합니다.", ex.message)
    }

    @Test
    fun `assign rejects an unknown source id`() {
        assertThrows(NoSuchElementException::class.java) {
            scheduleService.assign(9999L, date, 600)
        }
    }

    @Test
    fun `assign allows two sources to overlap in time`() {
        val a = newSource("A")
        val b = newSource("B")

        scheduleService.assign(a.id, date, 600)
        scheduleService.assign(b.id, date, 600)

        assertEquals(600, sourceRepository.findById(a.id).get().startMinutes)
        assertEquals(600, sourceRepository.findById(b.id).get().startMinutes)
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인한다**

Run: `./gradlew test --tests 'com.juiceplan.schedule.ScheduleServiceTest'`
Expected: 컴파일 실패 — `Unresolved reference: assign`, `Unresolved reference: startMinutes`

- [ ] **Step 3: `Source.kt`에서 `sortOrder`를 `startMinutes`로 바꾼다**

```kotlin
package com.juiceplan.source

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.time.LocalDate

enum class PlaceType { RESTAURANT, ATTRACTION }

@Entity
class Source(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    var googleMapsUrl: String,
    var name: String,
    var latitude: Double,
    var longitude: Double,

    @Enumerated(EnumType.STRING)
    var placeType: PlaceType,

    var durationMinutes: Int,
    var reservationRequired: Boolean,
    var reservationDeadline: LocalDate? = null,
    var memo: String? = null,

    // 배정된 날짜. startMinutes와 항상 함께 설정되거나 함께 null이다.
    var scheduledDate: LocalDate? = null,

    // 배정된 시작 시각. 자정 기준 분(240=04:00 ~ 1650=27:30), 30의 배수.
    // 그리드는 28:00(1680)까지 그리지만 28:00은 아래 경계라 시작 시각이 될 수 없다.
    // 04~28시 그리드를 다루기 위해 LocalTime 대신 정수를 쓴다 (LocalTime은 28:00을 표현할 수 없다).
    var startMinutes: Int? = null
)
```

- [ ] **Step 4: `ScheduleService.kt`를 단건 배정으로 다시 쓴다**

```kotlin
package com.juiceplan.schedule

import com.juiceplan.source.SourceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/** 타임테이블 하루의 시작(04:00)을 자정 기준 분으로 나타낸 값. */
const val DAY_START_MINUTES = 240

/** 타임테이블 그리드의 아래 경계(28:00 = 다음날 04:00). 배정 가능한 시각이 아니다. */
const val DAY_END_MINUTES = 1680

/** 타임테이블 한 슬롯의 길이(분). 배정 시각은 이 값의 배수여야 한다. */
const val SLOT_MINUTES = 30

/**
 * 배정 가능한 마지막 시작 시각(27:30).
 * 28:00은 그리드의 아래 경계여서 블록을 그릴 높이가 남지 않으므로 시작 시각이 될 수 없다.
 */
const val LAST_START_MINUTES = DAY_END_MINUTES - SLOT_MINUTES

@Service
class ScheduleService(private val sourceRepository: SourceRepository) {

    @Transactional
    fun assign(sourceId: Long, date: LocalDate, startMinutes: Int) {
        require(startMinutes in DAY_START_MINUTES..LAST_START_MINUTES) {
            "시간은 04:00~27:30 사이여야 합니다."
        }
        require(startMinutes % SLOT_MINUTES == 0) {
            "시간은 30분 단위여야 합니다."
        }

        val source = sourceRepository.findById(sourceId)
            .orElseThrow { NoSuchElementException("소스를 찾을 수 없습니다: $sourceId") }
        source.scheduledDate = date
        source.startMinutes = startMinutes
    }

    @Transactional
    fun remove(sourceId: Long) {
        val source = sourceRepository.findById(sourceId)
            .orElseThrow { NoSuchElementException("소스를 찾을 수 없습니다: $sourceId") }
        source.scheduledDate = null
        source.startMinutes = null
    }
}
```

- [ ] **Step 5: 테스트가 통과하는지 확인한다**

Run: `./gradlew test --tests 'com.juiceplan.schedule.ScheduleServiceTest'`
Expected: 9개 테스트 전부 PASS

- [ ] **Step 6: 커밋한다**

```bash
git add src/main/kotlin/com/juiceplan/source/Source.kt \
        src/main/kotlin/com/juiceplan/schedule/ScheduleService.kt \
        src/test/kotlin/com/juiceplan/schedule/ScheduleServiceTest.kt
git commit -m "refactor: 소스 배정을 순서(sortOrder)에서 시각(startMinutes)으로 전환"
```

---

## Task 2: 배정 API를 단건 PUT으로 교체

**Files:**
- Modify: `src/main/kotlin/com/juiceplan/schedule/ScheduleController.kt`
- Test: `src/test/kotlin/com/juiceplan/schedule/ScheduleControllerIntegrationTest.kt`

**Interfaces:**
- Consumes: `ScheduleService.assign(sourceId, date, startMinutes)`, `ScheduleService.remove(sourceId)` (Task 1)
- Produces:
  - `PUT /api/schedule/{sourceId}` — 본문 `{"date":"2026-09-01","startMinutes":600}`, 성공 200, 검증 실패 400
  - `DELETE /api/schedule/{sourceId}` — 성공 200
  - `data class AssignRequest(val date: LocalDate, val startMinutes: Int)`

`IllegalArgumentException`은 기존 `GlobalExceptionHandler`가 `/api/` 경로에서 400 + `{"error": "..."}`로 변환한다. 컨트롤러에서 따로 잡지 않는다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`src/test/kotlin/com/juiceplan/schedule/ScheduleControllerIntegrationTest.kt` 전체를 아래로 교체한다.

```kotlin
package com.juiceplan.schedule

import com.juiceplan.auth.SESSION_AUTHENTICATED_KEY
import com.juiceplan.source.PlaceType
import com.juiceplan.source.Source
import com.juiceplan.source.SourceRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScheduleControllerIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var sourceRepository: SourceRepository

    private lateinit var session: MockHttpSession

    @BeforeEach
    fun setUp() {
        sourceRepository.deleteAll()
        session = MockHttpSession()
        session.setAttribute(SESSION_AUTHENTICATED_KEY, true)
    }

    private fun newSource(name: String) = sourceRepository.save(
        Source(
            googleMapsUrl = "https://maps.app.goo.gl/x",
            name = name,
            latitude = 37.0,
            longitude = 127.0,
            placeType = PlaceType.ATTRACTION,
            durationMinutes = 60,
            reservationRequired = false
        )
    )

    @Test
    fun `assigns a source to a date and time`() {
        val a = newSource("A")

        mockMvc.perform(
            put("/api/schedule/${a.id}").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"date":"2026-09-01","startMinutes":600}""")
        ).andExpect(status().isOk)

        val reloaded = sourceRepository.findById(a.id).get()
        assertEquals(LocalDate.of(2026, 9, 1), reloaded.scheduledDate)
        assertEquals(600, reloaded.startMinutes)
    }

    @Test
    fun `rejects a time outside the 0400 to 2800 window with 400`() {
        val a = newSource("A")

        mockMvc.perform(
            put("/api/schedule/${a.id}").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"date":"2026-09-01","startMinutes":120}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `rejects a time that is not on a 30 minute slot with 400`() {
        val a = newSource("A")

        mockMvc.perform(
            put("/api/schedule/${a.id}").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"date":"2026-09-01","startMinutes":615}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `removes a source from the schedule`() {
        val a = newSource("A")
        a.scheduledDate = LocalDate.of(2026, 9, 1)
        a.startMinutes = 600
        sourceRepository.save(a)

        mockMvc.perform(delete("/api/schedule/${a.id}").session(session))
            .andExpect(status().isOk)

        val reloaded = sourceRepository.findById(a.id).get()
        assertNull(reloaded.scheduledDate)
        assertNull(reloaded.startMinutes)
    }

    @Test
    fun `unauthenticated request is blocked with 401, not a redirect`() {
        mockMvc.perform(
            put("/api/schedule/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"date":"2026-09-01","startMinutes":600}""")
        ).andExpect(status().isUnauthorized)
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인한다**

Run: `./gradlew test --tests 'com.juiceplan.schedule.ScheduleControllerIntegrationTest'`
Expected: `assigns a source to a date and time`이 405(Method Not Allowed) 또는 404로 FAIL

- [ ] **Step 3: 컨트롤러를 다시 쓴다**

```kotlin
package com.juiceplan.schedule

import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

data class AssignRequest(val date: LocalDate, val startMinutes: Int)

@RestController
class ScheduleController(private val scheduleService: ScheduleService) {

    @PutMapping("/api/schedule/{sourceId}")
    fun assign(@PathVariable sourceId: Long, @RequestBody request: AssignRequest) {
        scheduleService.assign(sourceId, request.date, request.startMinutes)
    }

    @DeleteMapping("/api/schedule/{sourceId}")
    fun remove(@PathVariable sourceId: Long) {
        scheduleService.remove(sourceId)
    }
}
```

- [ ] **Step 4: 테스트가 통과하는지 확인한다**

Run: `./gradlew test --tests 'com.juiceplan.schedule.ScheduleControllerIntegrationTest'`
Expected: 5개 테스트 전부 PASS

- [ ] **Step 5: 커밋한다**

```bash
git add src/main/kotlin/com/juiceplan/schedule/ScheduleController.kt \
        src/test/kotlin/com/juiceplan/schedule/ScheduleControllerIntegrationTest.kt
git commit -m "feat: 배정 API를 날짜+시각 단건 PUT으로 교체"
```

---

## Task 3: 기존 데이터 마이그레이션

`ddl-auto: update`는 컬럼을 추가만 하고 삭제하지 않는다. 엔티티에서 `sortOrder`를 뺐으므로 DB에는 `SORT_ORDER NOT NULL` 컬럼이 남아 **새 소스 저장이 전부 실패한다.** 이 태스크가 그걸 고친다.

`ApplicationRunner`로 등록하면 Hibernate가 `START_MINUTES` 컬럼을 만든 뒤에 실행된다.

**Files:**
- Create: `src/main/kotlin/com/juiceplan/schedule/ScheduleTimeMigration.kt`
- Test: `src/test/kotlin/com/juiceplan/schedule/ScheduleTimeMigrationTest.kt`

**Interfaces:**
- Consumes: `DAY_END_MINUTES`, `SLOT_MINUTES` (Task 1)
- Produces: `ScheduleTimeMigration.migrate()` — 테스트에서 직접 호출할 수 있게 `run()`과 분리한 public 메서드

**변환 규칙:** 날짜별로 `sort_order` 오름차순 정렬 → 첫 소스는 10:00(600분) → 이후 각 소스는 `직전 시작 + 직전 소요시간 + 30분`을 **30분 위로 올림** → 1650(27:30)을 넘으면 1650으로 고정.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`src/test/kotlin/com/juiceplan/schedule/ScheduleTimeMigrationTest.kt`

```kotlin
package com.juiceplan.schedule

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class ScheduleTimeMigrationTest {

    @Autowired lateinit var jdbcTemplate: JdbcTemplate
    @Autowired lateinit var migration: ScheduleTimeMigration

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("DELETE FROM SOURCE")
        // 앱 기동 시 migration이 이미 한 번 돌아 컬럼을 지웠을 수 있으므로 되살린다.
        jdbcTemplate.execute("ALTER TABLE SOURCE ADD COLUMN IF NOT EXISTS SORT_ORDER INT NOT NULL DEFAULT 0")
    }

    private fun insert(id: Long, name: String, date: String?, sortOrder: Int, durationMinutes: Int) {
        jdbcTemplate.update(
            """
            INSERT INTO SOURCE
              (ID, GOOGLE_MAPS_URL, NAME, LATITUDE, LONGITUDE, PLACE_TYPE,
               DURATION_MINUTES, RESERVATION_REQUIRED, SCHEDULED_DATE, START_MINUTES, SORT_ORDER)
            VALUES (?, 'https://maps.app.goo.gl/x', ?, 37.0, 127.0, 'ATTRACTION', ?, FALSE, ?, NULL, ?)
            """.trimIndent(),
            id, name, durationMinutes, date, sortOrder
        )
    }

    private fun startMinutesOf(id: Long): Int? =
        jdbcTemplate.queryForObject("SELECT START_MINUTES FROM SOURCE WHERE ID = ?", Int::class.javaObjectType, id)

    private fun sortOrderColumnExists(): Boolean =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_NAME = 'SOURCE' AND COLUMN_NAME = 'SORT_ORDER'
            """.trimIndent(),
            Int::class.java
        )!! > 0

    @Test
    fun `fills start times sequentially from 1000 within each date`() {
        insert(1, "A", "2026-09-01", 0, 60)
        insert(2, "B", "2026-09-01", 1, 90)
        insert(3, "C", "2026-09-01", 2, 30)

        migration.migrate()

        // A: 10:00 = 600
        assertEquals(600, startMinutesOf(1))
        // B: 600 + 60 + 30 = 690 (11:30)
        assertEquals(690, startMinutesOf(2))
        // C: 690 + 90 + 30 = 810 (13:30)
        assertEquals(810, startMinutesOf(3))
    }

    @Test
    fun `each date restarts from 1000`() {
        insert(1, "A", "2026-09-01", 0, 60)
        insert(2, "B", "2026-09-02", 0, 60)

        migration.migrate()

        assertEquals(600, startMinutesOf(1))
        assertEquals(600, startMinutesOf(2))
    }

    @Test
    fun `rounds up to the next 30 minute slot when duration is not a multiple of 30`() {
        insert(1, "A", "2026-09-01", 0, 45)
        insert(2, "B", "2026-09-01", 1, 60)

        migration.migrate()

        assertEquals(600, startMinutesOf(1))
        // 600 + 45 + 30 = 675 -> 30분 위로 올림 -> 690
        assertEquals(690, startMinutesOf(2))
    }

    @Test
    fun `clamps to 2800 when the day overflows`() {
        insert(1, "A", "2026-09-01", 0, 600)
        insert(2, "B", "2026-09-01", 1, 600)

        migration.migrate()

        assertEquals(600, startMinutesOf(1))
        // 600 + 600 + 30 = 1230 (20:30)
        assertEquals(1230, startMinutesOf(2))
    }

    @Test
    fun `leaves unscheduled sources with a null start time`() {
        insert(1, "A", null, 0, 60)

        migration.migrate()

        assertEquals(null, startMinutesOf(1))
    }

    @Test
    fun `drops the sort_order column`() {
        insert(1, "A", "2026-09-01", 0, 60)

        migration.migrate()

        assertEquals(false, sortOrderColumnExists())
    }

    @Test
    fun `running twice is safe`() {
        insert(1, "A", "2026-09-01", 0, 60)

        migration.migrate()
        migration.migrate()

        assertEquals(600, startMinutesOf(1))
        assertEquals(false, sortOrderColumnExists())
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인한다**

Run: `./gradlew test --tests 'com.juiceplan.schedule.ScheduleTimeMigrationTest'`
Expected: 컴파일 실패 — `Unresolved reference: ScheduleTimeMigration`

- [ ] **Step 3: 마이그레이션을 구현한다**

`src/main/kotlin/com/juiceplan/schedule/ScheduleTimeMigration.kt`

```kotlin
package com.juiceplan.schedule

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 마이그레이션으로 채워지는 첫 일정의 시작 시각 (10:00). */
private const val MIGRATION_FIRST_START_MINUTES = 600

/** 마이그레이션이 일정 사이에 넣는 이동 시간(분). */
private const val MIGRATION_GAP_MINUTES = 30

/**
 * sortOrder(순서) 기반 배정을 startMinutes(시각) 기반으로 옮기는 1회성 마이그레이션.
 *
 * ddl-auto: update는 컬럼을 추가만 하고 삭제하지 않으므로, 엔티티에서 sortOrder를 뺀 뒤에도
 * DB에는 SORT_ORDER NOT NULL 컬럼이 남아 새 소스 저장이 전부 실패한다. 이 클래스가 값을 옮기고
 * 컬럼을 지운다. SORT_ORDER 컬럼이 없으면 아무것도 하지 않으므로 몇 번 실행해도 안전하다.
 *
 * ApplicationRunner로 등록해 Hibernate가 START_MINUTES 컬럼을 만든 뒤에 실행되도록 한다.
 */
@Component
class ScheduleTimeMigration(private val jdbcTemplate: JdbcTemplate) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        migrate()
    }

    @Transactional
    fun migrate() {
        if (!sortOrderColumnExists()) return

        val rows = jdbcTemplate.queryForList(
            """
            SELECT ID, SCHEDULED_DATE, DURATION_MINUTES
            FROM SOURCE
            WHERE SCHEDULED_DATE IS NOT NULL
            ORDER BY SCHEDULED_DATE, SORT_ORDER
            """.trimIndent()
        )

        var currentDate: Any? = null
        var nextStart = MIGRATION_FIRST_START_MINUTES

        for (row in rows) {
            val date = row["SCHEDULED_DATE"]
            if (date != currentDate) {
                currentDate = date
                nextStart = MIGRATION_FIRST_START_MINUTES
            }

            val start = minOf(nextStart, LAST_START_MINUTES)
            jdbcTemplate.update("UPDATE SOURCE SET START_MINUTES = ? WHERE ID = ?", start, row["ID"])

            val duration = (row["DURATION_MINUTES"] as Number).toInt()
            nextStart = roundUpToSlot(start + duration + MIGRATION_GAP_MINUTES)
        }

        jdbcTemplate.execute("ALTER TABLE SOURCE DROP COLUMN IF EXISTS SORT_ORDER")
    }

    /** durationMinutes는 30의 배수가 아닐 수 있으므로, 배정 시각이 30분 슬롯에 맞도록 위로 올린다. */
    private fun roundUpToSlot(minutes: Int): Int =
        ((minutes + SLOT_MINUTES - 1) / SLOT_MINUTES) * SLOT_MINUTES

    private fun sortOrderColumnExists(): Boolean =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_NAME = 'SOURCE' AND COLUMN_NAME = 'SORT_ORDER'
            """.trimIndent(),
            Int::class.java
        )!! > 0
}
```

- [ ] **Step 4: 테스트가 통과하는지 확인한다**

Run: `./gradlew test --tests 'com.juiceplan.schedule.ScheduleTimeMigrationTest'`
Expected: 7개 테스트 전부 PASS

- [ ] **Step 5: 전체 테스트를 돌려 남은 컴파일 오류를 찾는다**

Run: `./gradlew test`
Expected: `sortOrder`를 참조하는 다른 테스트가 있으면 컴파일 실패한다. 있으면 `startMinutes` 기준으로 고친다 (예: `assertEquals(0, x.sortOrder)` → 해당 단언 삭제). 없으면 전부 PASS.

- [ ] **Step 6: 커밋한다**

```bash
git add src/main/kotlin/com/juiceplan/schedule/ScheduleTimeMigration.kt \
        src/test/kotlin/com/juiceplan/schedule/ScheduleTimeMigrationTest.kt
git add -u
git commit -m "feat: 기존 순서 기반 배정을 시각으로 옮기고 sort_order 컬럼 제거"
```

---

## Task 4: 디자인 토큰과 공용 레이아웃

여기서부터 화면 작업이다. 자동 테스트가 없으므로 각 태스크 끝에 **직접 확인 항목**을 둔다.

앱 실행: `./gradlew bootRun` (8080 포트가 이미 쓰이면 먼저 내린다). 브라우저 devtools에서 모바일 에뮬레이션(iPhone 12 Pro, 390×844) + 터치 활성으로 확인한다.

**Files:**
- Modify: `src/main/resources/static/css/style.css` (전면 재작성)
- Modify: `src/main/resources/templates/fragments/layout.html`
- Modify: `src/main/resources/templates/auth/login.html`
- Modify: `src/main/resources/templates/auth/setup.html`
- Modify: `src/main/kotlin/com/juiceplan/config/WebConfig.kt`

**Interfaces:**
- Produces (다음 태스크들이 그대로 쓴다):
  - Thymeleaf fragment `~{fragments/layout :: head(title)}` — `<meta charset>`, viewport, `<title>`, `style.css` 링크
  - Thymeleaf fragment `~{fragments/layout :: tabs(active)}` — `active` 값은 `'sources'` | `'plan'` | `'day'`
  - CSS 클래스: `.card`, `.badge`, `.badge--food`, `.badge--attraction`, `.badge--reservation`, `.chip`, `.chip--on`, `.sheet`, `.sheet--open`, `.sheet__backdrop`, `.fab`, `.btn`, `.btn--primary`, `.btn--ghost`, `.date-strip`, `.date-strip__item`, `.date-strip__item--on`, `.muted`
  - CSS 변수: `--bg --surface --primary --food --attraction --text --muted --border --radius --radius-sm --slot-h`

- [ ] **Step 1: `WebConfig`에 `/day`를 추가한다**

`/day`가 인터셉터 목록에 없으면 로그인 없이 여행 일정이 노출된다.

```kotlin
registry.addInterceptor(authInterceptor)
    .addPathPatterns(
        "/sources", "/sources/**",
        "/plan", "/plan/**",
        "/day", "/day/**",
        "/trip/**",
        "/api/**"
    )
```

- [ ] **Step 2: `style.css`를 토큰 + 컴포넌트로 다시 쓴다**

기존 9줄을 전부 지우고 아래 구조로 작성한다. 값은 스펙 표를 그대로 따른다.

```css
/* ---- 디자인 토큰 ---- */
:root {
  --bg: #F5F6F8;
  --surface: #FFFFFF;
  --primary: #4F6BED;
  --food: #FF7A59;
  --attraction: #2BB0A0;
  --text: #1B1F27;
  --muted: #6B7280;
  --border: #E5E7EB;
  --radius: 16px;
  --radius-sm: 10px;
  --slot-h: 28px;        /* 타임테이블 30분 슬롯 높이 */
  --shadow: 0 1px 3px rgb(0 0 0 / .06), 0 1px 2px rgb(0 0 0 / .04);
}

@media (prefers-color-scheme: dark) {
  :root {
    --bg: #14161A;
    --surface: #1E2127;
    --primary: #7C90F5;
    --food: #FF9273;
    --attraction: #48C7B7;
    --text: #ECEEF2;
    --muted: #9AA1AD;
    --border: #2E323A;
    --shadow: 0 1px 3px rgb(0 0 0 / .4);
  }
}

/* ---- 기본 ---- */
* { box-sizing: border-box; }
body {
  margin: 0;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "Noto Sans KR", sans-serif;
  background: var(--bg);
  color: var(--text);
  /* 하단 탭바(56px) + 홈 인디케이터 */
  padding-bottom: calc(56px + env(safe-area-inset-bottom));
}
.muted { color: var(--muted); font-size: 13px; }

/* ---- 컴포넌트 ---- */
.card {
  background: var(--surface);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 14px 16px;
  margin: 8px;
}

.btn {
  min-height: 44px;
  padding: 0 16px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  background: var(--surface);
  color: var(--text);
  font-size: 15px;
}
.btn--primary { background: var(--primary); border-color: var(--primary); color: #fff; font-weight: 600; }
.btn--ghost { background: transparent; border-color: transparent; color: var(--muted); }

.badge {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  font-size: 12px; font-weight: 600;
  color: #fff;
}
.badge--food { background: var(--food); }
.badge--attraction { background: var(--attraction); }
.badge--reservation { background: transparent; color: var(--muted); border: 1px solid var(--border); }

.chip {
  min-height: 44px;
  padding: 0 14px;
  border-radius: 22px;
  border: 1px solid var(--border);
  background: var(--surface);
  color: var(--muted);
  font-size: 14px;
}
.chip--on { background: var(--primary); border-color: var(--primary); color: #fff; font-weight: 600; }

.date-strip {
  display: flex; gap: 8px;
  overflow-x: auto;
  padding: 8px;
  scrollbar-width: none;
}
.date-strip::-webkit-scrollbar { display: none; }
.date-strip__item {
  flex: 0 0 auto;
  min-width: 56px; min-height: 64px;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 2px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  background: var(--surface);
  color: var(--text);
  font-size: 13px;
}
.date-strip__item--on {
  background: var(--primary); border-color: var(--primary); color: #fff;
}
/* 선택된 날짜 안의 .muted 는 파란 배경 위에 놓이므로 대비를 되돌린다 */
.date-strip__item--on .muted { color: rgb(255 255 255 / .75); }

input, select, textarea {
  width: 100%;
  min-height: 44px;
  padding: 8px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--text);
  font: inherit;
}
label { display: block; margin: 12px 0 4px; font-size: 14px; }

/* ---- 하단 탭바 ---- */
footer {
  position: fixed; bottom: 0; left: 0; right: 0;
  background: var(--surface);
  border-top: 1px solid var(--border);
  padding-bottom: env(safe-area-inset-bottom);
}
footer nav { display: flex; }
footer nav a {
  flex: 1; min-height: 56px;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 2px;
  text-decoration: none; color: var(--muted); font-size: 11px;
}
footer nav a.active { color: var(--primary); font-weight: 600; }
```

- [ ] **Step 3: `layout.html`에 `head` fragment를 추가하고 탭을 3개로 늘린다**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head th:fragment="head(title)">
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <title th:text="${title}">juice-plan</title>
    <link rel="stylesheet" th:href="@{/css/style.css}">
</head>
<body>
<footer th:fragment="tabs(active)">
    <nav>
        <a href="/sources" th:classappend="${active == 'sources'} ? 'active' : ''">
            <span>📋</span><span>소스 관리</span>
        </a>
        <a href="/plan" th:classappend="${active == 'plan'} ? 'active' : ''">
            <span>🗓️</span><span>일자별 동선</span>
        </a>
        <a href="/day" th:classappend="${active == 'day'} ? 'active' : ''">
            <span>🧭</span><span>계획 보기</span>
        </a>
    </nav>
</footer>
</body>
</html>
```

`viewport-fit=cover`가 있어야 `env(safe-area-inset-bottom)`이 동작한다.

- [ ] **Step 4: `login.html`과 `setup.html`에 새 디자인을 입힌다**

두 파일의 `<head>`를 `<head th:replace="~{fragments/layout :: head('로그인')}"></head>` (setup은 `'초기 설정'`)로 바꾸고, 폼을 `.card` 안에 넣고 버튼에 `.btn.btn--primary`를 붙인다. 이 두 화면에는 하단 탭을 넣지 않는다 (아직 로그인 전이다).

- [ ] **Step 5: 애플리케이션이 뜨는지 확인한다**

Run: `./gradlew test` — 기존 테스트가 전부 PASS해야 한다 (`AuthFlowIntegrationTest`가 로그인 화면 렌더링을 확인한다).
Run: `./gradlew bootRun` 후 브라우저에서 `http://localhost:8080/` 확인.

직접 확인:
- [ ] 로그인 화면이 카드 형태로 보이고 버튼이 44px 이상이다
- [ ] devtools에서 다크모드로 전환하면 색이 바뀌고 글자가 읽힌다
- [ ] 하단 탭이 3개 보이고 아이콘 + 라벨이 나온다

- [ ] **Step 6: 커밋한다**

```bash
git add src/main/resources/static/css/style.css \
        src/main/resources/templates/fragments/layout.html \
        src/main/resources/templates/auth/login.html \
        src/main/resources/templates/auth/setup.html \
        src/main/kotlin/com/juiceplan/config/WebConfig.kt
git commit -m "feat: 디자인 토큰 CSS와 3탭 공용 레이아웃 도입"
```

---

## Task 5: 소스 관리 화면 재작성

**Files:**
- Modify: `src/main/resources/templates/sources/index.html`
- Modify: `src/main/resources/static/js/sources.js`
- Modify: `src/main/resources/static/css/style.css` (소스 화면 전용 규칙 추가)

**Interfaces:**
- Consumes: Task 4의 fragment와 CSS 클래스
- Produces: 없음 (다른 태스크가 의존하지 않는다)

**동작:** 상단 여행 기간 카드 → 필터 칩(전체/음식점/관광지/미배정) → 소스 카드 리스트 → 우하단 FAB(+). 추가/수정은 같은 하단 시트를 재사용한다.

- [ ] **Step 1: `sources/index.html`을 다시 쓴다**

- `<head>`를 `~{fragments/layout :: head('소스 관리')}`로 교체
- 여행 기간을 `.card`로 감싼다
- 필터 칩: `<button class="chip chip--on" data-filter="all">전체</button>` 형태로 4개. 서버 렌더 없이 JS가 카드를 숨긴다.
- 소스 카드는 아래 마크업을 쓴다. `data-place-type`과 `data-scheduled`는 필터 칩 JS가 읽으므로 이름을 바꾸면 안 된다. 수정 버튼의 `data-*` 속성은 **기존 파일에서 그대로 옮겨온다** (`sources.js`의 수정 핸들러가 전부 읽는다).

```html
<ul style="list-style:none; padding:0; margin:0;">
    <li th:each="s : ${sources}" class="card source-card"
        th:attr="data-place-type=${s.placeType}, data-scheduled=${s.scheduledDate != null}">
        <div>
            <span th:if="${s.placeType.name() == 'RESTAURANT'}" class="badge badge--food">🍴 음식점</span>
            <span th:unless="${s.placeType.name() == 'RESTAURANT'}" class="badge badge--attraction">📍 관광지</span>
            <strong th:text="${s.name}">이름</strong>
        </div>
        <div class="muted">
            <span th:text="${s.durationMinutes} + '분'">60분</span> ·
            <span th:if="${s.scheduledDate != null}"
                  th:text="${s.scheduledDate} + ' ' +
                           ${#numbers.formatInteger((s.startMinutes % 1440) / 60, 2)} + ':' +
                           ${#numbers.formatInteger(s.startMinutes % 60, 2)}">2026-08-15 11:00</span>
            <span th:if="${s.scheduledDate == null}">미배정</span>
        </div>
        <div th:if="${s.reservationRequired}" class="badge badge--reservation"
             th:text="'🔔 예약 마감 ' + ${s.reservationDeadline}">🔔 예약 마감</div>
        <p th:if="${s.memo != null}" th:text="${s.memo}" class="muted"></p>
        <button type="button" class="btn edit-btn" th:attr="data-id=${s.id}, ...">수정</button>
        <button type="button" class="btn delete-btn" th:attr="data-id=${s.id}">삭제</button>
    </li>
</ul>
```

`data-id=${s.id}, ...` 부분은 기존 `sources/index.html`의 `edit-btn`에 있던 `data-google-maps-url` ~ `data-memo` 속성 10개를 **한 글자도 바꾸지 말고 그대로** 붙여넣는다.

`% 1440`은 25:00~28:00을 01:00~04:00으로 접는다. 소스 목록에서는 그 표기가 자연스럽다.
- 추가/수정 폼 전체를 `<div class="sheet" id="sourceSheet">` 안으로 옮긴다. 폼 필드와 `name` 속성은 **하나도 바꾸지 않는다** — 서버의 `SourceForm` 바인딩이 그대로 동작해야 한다.
- `<div class="sheet__backdrop" id="sheetBackdrop"></div>`와 `<button class="fab" id="addSourceBtn">+</button>` 추가

- [ ] **Step 2: `sources.js`에 시트 열고 닫기와 필터를 추가한다**

기존 코드는 유지하고 아래를 더한다. 수정 버튼 핸들러 끝에 `openSheet()`를 호출하도록 바꾸고, `scrollIntoView` 호출은 지운다.

```js
const sheet = document.getElementById('sourceSheet');
const backdrop = document.getElementById('sheetBackdrop');

function openSheet() {
    sheet.classList.add('sheet--open');
    backdrop.classList.add('sheet--open');
}

function closeSheet() {
    sheet.classList.remove('sheet--open');
    backdrop.classList.remove('sheet--open');
}

document.getElementById('addSourceBtn').addEventListener('click', () => {
    editingId = null;
    sourceForm.reset();
    document.getElementById('reservationDeadlineWrap').style.display = 'none';
    submitBtn.textContent = '저장';
    openSheet();
});

backdrop.addEventListener('click', closeSheet);
document.getElementById('sheetClose').addEventListener('click', closeSheet);

// 필터 칩: 카드의 data-place-type / data-scheduled 로 숨긴다
document.querySelectorAll('.chip[data-filter]').forEach((chip) => {
    chip.addEventListener('click', () => {
        document.querySelectorAll('.chip[data-filter]').forEach((c) => c.classList.remove('chip--on'));
        chip.classList.add('chip--on');
        const filter = chip.dataset.filter;
        document.querySelectorAll('.source-card').forEach((card) => {
            const show =
                filter === 'all' ||
                (filter === 'unassigned' && card.dataset.scheduled === 'false') ||
                filter === card.dataset.placeType;
            card.style.display = show ? '' : 'none';
        });
    });
});
```

시트에 닫기 버튼 `<button type="button" id="sheetClose" class="btn btn--ghost">닫기</button>`을 넣는 걸 잊지 않는다.

- [ ] **Step 3: 시트/칩/FAB CSS를 `style.css`에 추가한다**

```css
.sheet {
  position: fixed; left: 0; right: 0; bottom: 0; z-index: 20;
  background: var(--surface);
  border-radius: var(--radius) var(--radius) 0 0;
  padding: 20px 16px calc(20px + env(safe-area-inset-bottom));
  max-height: 85vh; overflow-y: auto;
  transform: translateY(100%);
  transition: transform .25s ease;
}
.sheet.sheet--open { transform: translateY(0); }

.sheet__backdrop {
  position: fixed; inset: 0; z-index: 10;
  background: rgb(0 0 0 / .4);
  opacity: 0; pointer-events: none;
  transition: opacity .25s ease;
}
.sheet__backdrop.sheet--open { opacity: 1; pointer-events: auto; }

.fab {
  position: fixed; right: 16px; z-index: 15;
  bottom: calc(72px + env(safe-area-inset-bottom));
  width: 56px; height: 56px; border-radius: 28px; border: 0;
  background: var(--primary); color: #fff; font-size: 28px;
  box-shadow: var(--shadow);
}
```

- [ ] **Step 4: 직접 확인한다**

Run: `./gradlew bootRun`, 브라우저 390px 모바일 에뮬레이션으로 `http://localhost:8080/sources`

- [ ] FAB(+)를 누르면 시트가 아래에서 올라온다
- [ ] 시트에서 소스를 추가하면 저장되고 목록에 나온다
- [ ] 수정 버튼을 누르면 값이 채워진 시트가 열리고, 저장하면 반영된다
- [ ] 배경을 누르면 시트가 닫힌다
- [ ] 필터 칩 4개가 각각 올바르게 걸러낸다
- [ ] 배정된 소스에 `8/15 11:00` 형태로 나오고 25시 이후 소스는 `01:00`으로 접혀 나온다
- [ ] 다크모드에서 시트와 카드가 읽힌다

- [ ] **Step 5: 회귀 테스트를 돌린다**

Run: `./gradlew test --tests 'com.juiceplan.source.*'`
Expected: 전부 PASS (폼 필드 `name`을 바꾸지 않았으므로 통과해야 한다)

- [ ] **Step 6: 커밋한다**

```bash
git add src/main/resources/templates/sources/index.html \
        src/main/resources/static/js/sources.js \
        src/main/resources/static/css/style.css
git commit -m "feat: 소스 관리 화면을 카드/필터칩/하단시트 구조로 재작성"
```

---

## Task 6: 타임그리드 계산 함수

드래그 UI를 붙이기 전에 순수 계산부터 만든다. 이 파일에는 DOM 접근이 없다.

**Files:**
- Create: `src/main/resources/static/js/timegrid.js`

**Interfaces:**
- Produces (전역 `window.TimeGrid`):
  - `DAY_START = 240`, `DAY_END = 1680`, `LAST_START = 1650`, `SLOT = 30`, `SLOT_H = 28`
  - `formatSlot(minutes) -> "25:30"` — 24시 이후는 25:00~28:00으로 표기
  - `snapToSlot(offsetY) -> minutes` — 그리드 상단 기준 y좌표(px)를 30분 스냅된 `startMinutes`로. `[240, 1650]`로 clamp
  - `topFor(startMinutes) -> px`
  - `heightFor(durationMinutes) -> px` — 최소 1슬롯
  - `layoutBlocks(blocks) -> [{ id, startMinutes, durationMinutes, column, columnCount }]` — 입력은 `{id, startMinutes, durationMinutes}` 배열

- [ ] **Step 1: `timegrid.js`를 작성한다**

```js
// 타임테이블 순수 계산. DOM에 접근하지 않으므로 콘솔에서 직접 호출해 검증할 수 있다.
window.TimeGrid = (function () {
    const DAY_START = 240;   // 04:00 — 그리드 상단
    const DAY_END = 1680;    // 28:00 — 그리드 하단 경계 (배정 가능한 시각이 아니다)
    const SLOT = 30;         // 슬롯 길이(분)
    const SLOT_H = 28;       // 슬롯 높이(px) — style.css의 --slot-h와 반드시 같아야 한다
    const LAST_START = DAY_END - SLOT;  // 27:30 — 배정 가능한 마지막 시작 시각

    function pad(n) {
        return String(n).padStart(2, '0');
    }

    // 24시 이후는 25:00~28:00으로 표기한다 (다음날 새벽을 이어서 보여주기 위함).
    function formatSlot(minutes) {
        return pad(Math.floor(minutes / 60)) + ':' + pad(minutes % 60);
    }

    function clamp(v, lo, hi) {
        return Math.max(lo, Math.min(v, hi));
    }

    // 그리드 상단(04:00) 기준 y좌표(px) -> 30분 스냅된 시작 분
    function snapToSlot(offsetY) {
        const raw = DAY_START + (offsetY / SLOT_H) * SLOT;
        const snapped = Math.round(raw / SLOT) * SLOT;
        return clamp(snapped, DAY_START, LAST_START);
    }

    function topFor(startMinutes) {
        return ((startMinutes - DAY_START) / SLOT) * SLOT_H;
    }

    function heightFor(durationMinutes) {
        return Math.max(SLOT_H, (durationMinutes / SLOT) * SLOT_H);
    }

    // 겹치는 블록을 구글 캘린더처럼 가로로 나눈다.
    // 1) 시작 시각 오름차순 정렬
    // 2) 시간이 이어지는 동안 하나의 그룹으로 묶는다
    // 3) 그룹 안에서 앞 블록과 겹치지 않는 가장 왼쪽 컬럼에 배정
    // 4) 그룹 전체가 같은 columnCount를 공유해 폭이 어긋나지 않게 한다
    function layoutBlocks(blocks) {
        const sorted = blocks
            .slice()
            .sort((a, b) => a.startMinutes - b.startMinutes || a.id - b.id);

        const result = [];
        let group = [];
        let groupEnd = -1;

        function flush() {
            if (group.length === 0) return;
            const columnCount = Math.max(...group.map((g) => g.column)) + 1;
            group.forEach((g) => result.push({ ...g, columnCount }));
            group = [];
            groupEnd = -1;
        }

        for (const b of sorted) {
            const end = b.startMinutes + Math.max(SLOT, b.durationMinutes);

            if (b.startMinutes >= groupEnd) {
                flush();
            }

            // 이 그룹에서 비어 있는 가장 왼쪽 컬럼을 찾는다
            let column = 0;
            while (group.some((g) => g.column === column && g.end > b.startMinutes)) {
                column += 1;
            }

            group.push({ ...b, end, column });
            groupEnd = Math.max(groupEnd, end);
        }
        flush();

        return result.map(({ end, ...rest }) => rest);
    }

    return { DAY_START, DAY_END, LAST_START, SLOT, SLOT_H, formatSlot, snapToSlot, topFor, heightFor, layoutBlocks };
})();
```

- [ ] **Step 2: 브라우저 콘솔로 검증한다**

`./gradlew bootRun` 후 `/plan`(아직 예전 화면이어도 무방)에서 `timegrid.js`를 임시로 로드하거나, `/sources` 화면에서 개발자도구 콘솔에 파일 내용을 붙여넣고 아래를 실행한다.

```js
TimeGrid.snapToSlot(0)            // 240
TimeGrid.snapToSlot(28)           // 270
TimeGrid.snapToSlot(-100)         // 240   (위로 넘겨도 04:00에서 멈춤)
TimeGrid.snapToSlot(999999)       // 1650  (아래로 넘겨도 27:30에서 멈춤 — 28:00은 경계라 배정 불가)
TimeGrid.formatSlot(1530)         // "25:30"
TimeGrid.topFor(600)              // 336
TimeGrid.heightFor(10)            // 28    (최소 1슬롯)

// 겹치지 않는 둘 -> 각각 단독 컬럼
TimeGrid.layoutBlocks([
  {id:1, startMinutes:600, durationMinutes:60},
  {id:2, startMinutes:720, durationMinutes:60},
]).map(b => [b.id, b.column, b.columnCount])
// [[1,0,1],[2,0,1]]

// 겹치는 둘 -> 나란히
TimeGrid.layoutBlocks([
  {id:1, startMinutes:600, durationMinutes:120},
  {id:2, startMinutes:660, durationMinutes:60},
]).map(b => [b.id, b.column, b.columnCount])
// [[1,0,2],[2,1,2]]
```

각 줄의 실제 출력이 주석과 같은지 확인한다.

- [ ] **Step 3: 커밋한다**

```bash
git add src/main/resources/static/js/timegrid.js
git commit -m "feat: 타임테이블 좌표/겹침 계산 함수 추가"
```

---

## Task 7: Pointer Events 드래그 엔진

HTML5 Drag and Drop은 모바일 브라우저에서 이벤트를 발생시키지 않는다. 제스처를 직접 다룬다. 이 파일은 DOM 조작만 하고 소스·일정 도메인은 모른다.

**Files:**
- Create: `src/main/resources/static/js/dragdrop.js`
- Modify: `src/main/resources/static/css/style.css` (고스트 CSS 추가)

**Interfaces:**
- Produces (전역 `window.DragDrop`):
  - `DragDrop.makeDraggable(el, { data, onStart, onMove, onDrop, onTap })`
    - `data` — `onMove`/`onDrop`에 그대로 전달되는 임의 값
    - `onStart(data)` — 드래그 시작 시 1회
    - `onMove(data, clientX, clientY)` — 포인터 이동마다
    - `onDrop(data, clientX, clientY)` — 손을 뗄 때 1회
    - `onTap(data)` — 6px 미만으로 움직이고 뗐을 때 (드래그 아님)

- [ ] **Step 1: `dragdrop.js`를 작성한다**

```js
// Pointer Events 기반 드래그. HTML5 Drag and Drop API는 모바일 브라우저에서
// dragstart/drop 이벤트를 발생시키지 않으므로 직접 구현한다.
window.DragDrop = (function () {
    const DRAG_THRESHOLD_PX = 6;   // 이보다 적게 움직이면 탭으로 본다

    function makeDraggable(el, opts) {
        // touch-action: none 이 없으면 브라우저 스크롤이 pointermove를 가로챈다.
        el.style.touchAction = 'none';

        el.addEventListener('pointerdown', (e) => {
            // 마우스는 주버튼만
            if (e.pointerType === 'mouse' && e.button !== 0) return;

            const startX = e.clientX;
            const startY = e.clientY;
            let dragging = false;
            let ghost = null;

            function onPointerMove(ev) {
                const dx = ev.clientX - startX;
                const dy = ev.clientY - startY;

                if (!dragging) {
                    if (Math.hypot(dx, dy) < DRAG_THRESHOLD_PX) return;
                    dragging = true;
                    // 포인터를 캡처해야 요소 밖으로 나가도 이벤트가 계속 온다.
                    el.setPointerCapture(ev.pointerId);
                    ghost = el.cloneNode(true);
                    ghost.classList.add('drag-ghost');
                    ghost.style.width = el.offsetWidth + 'px';
                    document.body.appendChild(ghost);
                    if (opts.onStart) opts.onStart(opts.data);
                }

                ghost.style.left = ev.clientX + 'px';
                ghost.style.top = ev.clientY + 'px';
                if (opts.onMove) opts.onMove(opts.data, ev.clientX, ev.clientY);
            }

            function onPointerUp(ev) {
                el.removeEventListener('pointermove', onPointerMove);
                el.removeEventListener('pointerup', onPointerUp);
                el.removeEventListener('pointercancel', onPointerUp);

                if (ghost) {
                    ghost.remove();
                    ghost = null;
                }

                if (dragging) {
                    if (opts.onDrop) opts.onDrop(opts.data, ev.clientX, ev.clientY);
                } else if (ev.type === 'pointerup') {
                    if (opts.onTap) opts.onTap(opts.data);
                }
            }

            el.addEventListener('pointermove', onPointerMove);
            el.addEventListener('pointerup', onPointerUp);
            el.addEventListener('pointercancel', onPointerUp);
        });
    }

    return { makeDraggable };
})();
```

- [ ] **Step 2: 고스트 CSS를 추가한다**

```css
.drag-ghost {
  position: fixed; z-index: 100;
  pointer-events: none;
  transform: translate(-50%, -50%);
  opacity: .85;
  box-shadow: 0 8px 24px rgb(0 0 0 / .25);
}
```

`pointer-events: none`이 없으면 고스트가 `elementFromPoint` 판정을 가로채 드롭 대상을 못 찾는다.

- [ ] **Step 3: 커밋한다**

이 파일은 다음 태스크에서 화면에 붙여야 동작을 볼 수 있다. 단독으로는 확인할 게 없다.

```bash
git add src/main/resources/static/js/dragdrop.js \
        src/main/resources/static/css/style.css
git commit -m "feat: Pointer Events 기반 드래그 엔진 추가 (모바일 대응)"
```

---

## Task 8: 일자별 동선 화면 재작성

이 계획의 핵심 태스크다. 날짜 스트립 → 지도 → 좌 소스 레일 + 우 타임테이블 → 참고사항.

**Files:**
- Modify: `src/main/resources/templates/plan/index.html`
- Modify: `src/main/resources/static/js/plan.js` (전면 재작성)
- Modify: `src/main/resources/static/css/style.css` (타임테이블 CSS 추가)

**Interfaces:**
- Consumes: `window.TimeGrid` (Task 6), `window.DragDrop` (Task 7), `PUT/DELETE /api/schedule/{id}` (Task 2), Task 4의 fragment/CSS
- Produces: 없음

- [ ] **Step 1: `plan/index.html`을 다시 쓴다**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragments/layout :: head('일자별 동선')}"></head>
<body>
<div th:if="${tripMissing}" class="card">
    <p>먼저 여행 기간을 설정해주세요.</p>
    <a class="btn btn--primary" href="/sources">소스 관리로 이동</a>
</div>

<div th:unless="${tripMissing}" id="plan-app"
     th:attr="data-trip-start=${trip.startDate}, data-trip-end=${trip.endDate}">

    <div class="date-strip" id="date-strip"></div>

    <div id="map-panel">
        <div id="map"></div>
        <button type="button" class="btn btn--ghost" id="map-toggle">지도 접기</button>
    </div>

    <div id="planner">
        <div id="source-rail"></div>
        <div id="timetable-scroll">
            <div id="timetable">
                <div id="hour-lines"></div>
                <div id="blocks"></div>
                <div id="drop-preview" hidden></div>
            </div>
        </div>
    </div>

    <div id="day-note" class="card">
        <textarea id="day-note-text" rows="2" placeholder="이 날짜의 참고사항을 입력하세요"></textarea>
        <button type="button" class="btn btn--primary" id="day-note-save">참고사항 저장</button>
    </div>

    <div class="sheet__backdrop" id="timeSheetBackdrop"></div>
    <div class="sheet" id="timeSheet">
        <h3 id="timeSheetTitle"></h3>
        <label>시작 시각
            <select id="timeSheetSelect"></select>
        </label>
        <button type="button" class="btn btn--primary" id="timeSheetSave">저장</button>
        <button type="button" class="btn btn--ghost" id="timeSheetRemove">배정 해제</button>
        <button type="button" class="btn btn--ghost" id="timeSheetClose">닫기</button>
    </div>

    <script th:inline="javascript">
        /*<![CDATA[*/
        var SOURCES = /*[[${sources}]]*/ [];
        var DAY_NOTES = /*[[${dayNotes}]]*/ {};
        /*]]>*/
    </script>
</div>

<div th:replace="~{fragments/layout :: tabs('plan')}"></div>

<script th:if="${!tripMissing}" th:src="@{/js/timegrid.js}"></script>
<script th:if="${!tripMissing}" th:src="@{/js/dragdrop.js}"></script>
<script th:if="${!tripMissing}" th:src="@{/js/plan.js}"></script>
<script th:if="${!tripMissing}"
        th:src="'https://maps.googleapis.com/maps/api/js?key=' + ${@environment.getProperty('app.google-maps-api-key')} + '&callback=initMap'"
        async defer></script>
</body>
</html>
```

`timegrid.js`와 `dragdrop.js`가 `plan.js`보다 **먼저** 로드돼야 한다.

- [ ] **Step 2: 타임테이블 CSS를 추가한다**

```css
#planner {
  display: flex;
  gap: 8px;
  padding: 8px;
  /* 화면 나머지를 채운다. 지도 패널 높이는 JS가 CSS 변수로 알려준다. */
  height: calc(100vh - var(--planner-offset, 320px));
  min-height: 240px;
}

#source-rail {
  flex: 0 0 34%;
  overflow-y: auto;
  display: flex; flex-direction: column; gap: 8px;
}

#timetable-scroll {
  flex: 1;
  overflow-y: auto;
  background: var(--surface);
  border-radius: var(--radius);
  border: 1px solid var(--border);
}

#timetable {
  position: relative;
  /* 48슬롯 × 슬롯 높이 */
  height: calc(48 * var(--slot-h));
}

.hour-line {
  position: absolute; left: 0; right: 0;
  border-top: 1px solid var(--border);
  font-size: 11px; color: var(--muted);
  padding-left: 4px;
}

#blocks { position: absolute; inset: 0; margin-left: 40px; }

.tt-block {
  position: absolute;
  border-radius: var(--radius-sm);
  padding: 4px 6px;
  font-size: 12px; color: #fff;
  overflow: hidden;
  border-left: 4px solid transparent;
}
.tt-block--food { background: var(--food); }
.tt-block--attraction { background: var(--attraction); }
.tt-block--reserved { border-left-color: #fff; }

#drop-preview {
  position: absolute; left: 40px; right: 0;
  background: var(--primary);
  opacity: .35;
  border-radius: var(--radius-sm);
  pointer-events: none;
  font-size: 12px; color: #fff; padding: 2px 6px;
}

#map { height: 100%; width: 100%; }
#map-panel { height: 200px; transition: height .2s ease; }
#map-panel.collapsed { height: 0; overflow: hidden; }
```

- [ ] **Step 3: `plan.js`를 다시 쓴다**

기존 파일을 전부 지우고 아래 구조로 작성한다.

```js
(function () {
    const appEl = document.getElementById('plan-app');
    const TG = window.TimeGrid;

    // ---- 날짜 목록 ----
    // 문자열로 계산한다. new Date(...) 는 타임존에 따라 하루씩 밀릴 수 있다.
    const days = [];
    {
        const cur = new Date(appEl.dataset.tripStart + 'T00:00:00');
        const end = new Date(appEl.dataset.tripEnd + 'T00:00:00');
        while (cur <= end) {
            const y = cur.getFullYear();
            const m = String(cur.getMonth() + 1).padStart(2, '0');
            const d = String(cur.getDate()).padStart(2, '0');
            days.push(`${y}-${m}-${d}`);
            cur.setDate(cur.getDate() + 1);
        }
    }

    let selectedDate = days[0];
    let map;
    const markers = {};
    let dayPath = null;

    function isUnauthorized(res) {
        if (res.status === 401) {
            alert('세션이 만료되었습니다. 다시 로그인해주세요.');
            window.location.href = '/';
            return true;
        }
        return false;
    }

    // ---- 렌더링 ----
    // renderDateStrip()   : 날짜 칩 (요일 + 일자 + 배정 개수)
    // renderRail()        : 미배정 소스 카드 (지도 영역 필터 적용)
    // renderTimetable()   : 시간선 + 블록. TimeGrid.layoutBlocks 로 겹침 배치
    // renderDayNote()     : 참고사항 textarea
    // renderMapForDay()   : 그 날 동선을 시간순 폴리라인으로

    function scheduledOn(date) {
        return SOURCES
            .filter((s) => s.scheduledDate === date && s.startMinutes != null)
            .sort((a, b) => a.startMinutes - b.startMinutes);
    }

    function renderTimetable() {
        const blocksEl = document.getElementById('blocks');
        blocksEl.innerHTML = '';

        const laid = TG.layoutBlocks(scheduledOn(selectedDate).map((s) => ({
            id: s.id,
            startMinutes: s.startMinutes,
            durationMinutes: s.durationMinutes,
        })));

        laid.forEach((b) => {
            const s = SOURCES.find((x) => x.id === b.id);
            const el = document.createElement('div');
            el.className = 'tt-block ' +
                (s.placeType === 'RESTAURANT' ? 'tt-block--food' : 'tt-block--attraction') +
                (s.reservationRequired ? ' tt-block--reserved' : '');
            el.dataset.id = String(s.id);

            const top = TG.topFor(b.startMinutes);
            // 28:00을 넘기는 블록은 그리드 밖으로 삐져나오지 않게 아래에서 자른다.
            const gridBottom = TG.topFor(TG.DAY_END);
            el.style.top = top + 'px';
            el.style.height = Math.min(TG.heightFor(b.durationMinutes), gridBottom - top) + 'px';
            el.style.width = `calc((100% - 4px) / ${b.columnCount})`;
            el.style.left = `calc((100% - 4px) / ${b.columnCount} * ${b.column})`;

            const end = b.startMinutes + b.durationMinutes;
            const endLabel = end > TG.DAY_END ? '28:00+' : TG.formatSlot(end);
            el.textContent = `${TG.formatSlot(b.startMinutes)}–${endLabel} ${s.name}`;

            attachDrag(el, s.id);
            blocksEl.appendChild(el);
        });
    }

    // ---- 드래그 ----
    // 소스 레일 카드와 타임테이블 블록 모두 같은 핸들러를 쓴다.
    function attachDrag(el, sourceId) {
        window.DragDrop.makeDraggable(el, {
            data: sourceId,
            onMove: (id, x, y) => showPreview(id, x, y),
            onDrop: (id, x, y) => commitDrop(id, x, y),
            onTap: (id) => openTimeSheet(id),
        });
    }

    function pointToStartMinutes(clientY) {
        const scroll = document.getElementById('timetable-scroll');
        const rect = scroll.getBoundingClientRect();
        return TG.snapToSlot(clientY - rect.top + scroll.scrollTop);
    }

    function overTimetable(x, y) {
        const rect = document.getElementById('timetable-scroll').getBoundingClientRect();
        return x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom;
    }

    function overRail(x, y) {
        const rect = document.getElementById('source-rail').getBoundingClientRect();
        return x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom;
    }

    function showPreview(sourceId, x, y) {
        const preview = document.getElementById('drop-preview');
        if (!overTimetable(x, y)) {
            preview.hidden = true;
            return;
        }
        const s = SOURCES.find((v) => v.id === sourceId);
        const start = pointToStartMinutes(y);
        preview.hidden = false;
        preview.style.top = TG.topFor(start) + 'px';
        preview.style.height = TG.heightFor(s.durationMinutes) + 'px';
        preview.textContent = `${TG.formatSlot(start)} – ${TG.formatSlot(Math.min(start + s.durationMinutes, TG.DAY_END))}`;
        autoScroll(y);
    }

    // 포인터가 타임테이블 위/아래 가장자리 40px 안에 있으면 스크롤한다.
    function autoScroll(clientY) {
        const scroll = document.getElementById('timetable-scroll');
        const rect = scroll.getBoundingClientRect();
        const EDGE = 40;
        if (clientY - rect.top < EDGE) scroll.scrollTop -= 12;
        else if (rect.bottom - clientY < EDGE) scroll.scrollTop += 12;
    }

    async function commitDrop(sourceId, x, y) {
        document.getElementById('drop-preview').hidden = true;

        if (overTimetable(x, y)) {
            await assign(sourceId, selectedDate, pointToStartMinutes(y));
        } else if (overRail(x, y)) {
            await unassign(sourceId);
        }
    }

    // ---- API ----
    async function assign(sourceId, date, startMinutes) {
        try {
            const res = await fetch(`/api/schedule/${sourceId}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ date, startMinutes }),
            });
            if (isUnauthorized(res)) return;
            if (!res.ok) throw new Error('요청 실패');

            const s = SOURCES.find((v) => v.id === sourceId);
            s.scheduledDate = date;
            s.startMinutes = startMinutes;
            renderAll();
        } catch (e) {
            alert('저장 실패, 다시 시도해주세요.');
            renderAll();
        }
    }

    async function unassign(sourceId) {
        try {
            const res = await fetch(`/api/schedule/${sourceId}`, { method: 'DELETE' });
            if (isUnauthorized(res)) return;
            if (!res.ok) throw new Error('요청 실패');

            const s = SOURCES.find((v) => v.id === sourceId);
            s.scheduledDate = null;
            s.startMinutes = null;
            renderAll();
        } catch (e) {
            alert('저장 실패, 다시 시도해주세요.');
            renderAll();
        }
    }

    function renderAll() {
        renderDateStrip();
        renderRail();
        renderTimetable();
        renderDayNote();
        renderMapForDay();
    }

    // 나머지 함수(renderDateStrip, renderRail, renderDayNote, saveDayNote,
    // renderMapForDay, openTimeSheet, initMap, 이벤트 바인딩)는 아래 Step 4~6에서 채운다.
})();
```

- [ ] **Step 4: 날짜 스트립, 소스 레일, 참고사항 렌더링을 채운다**

`renderAll` 위에 아래를 넣는다.

```js
    const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

    function renderDateStrip() {
        const strip = document.getElementById('date-strip');
        strip.innerHTML = '';
        days.forEach((date) => {
            const d = new Date(date + 'T00:00:00');
            const count = SOURCES.filter((s) => s.scheduledDate === date).length;

            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'date-strip__item' + (date === selectedDate ? ' date-strip__item--on' : '');
            btn.innerHTML =
                `<span class="muted">${WEEKDAYS[d.getDay()]}</span>` +
                `<strong>${d.getDate()}</strong>` +
                `<span class="muted">${count > 0 ? count + '곳' : ''}</span>`;
            btn.addEventListener('click', () => {
                selectedDate = date;
                renderAll();
            });
            strip.appendChild(btn);
        });
    }

    // 미배정 소스만 보여준다. 지도가 준비되면 지도 영역 안의 것만 남긴다.
    function railSources() {
        const unscheduled = SOURCES.filter((s) => !s.scheduledDate);
        if (!map) return unscheduled;
        const bounds = map.getBounds();
        if (!bounds) return unscheduled;
        return unscheduled.filter((s) => bounds.contains({ lat: s.latitude, lng: s.longitude }));
    }

    function renderRail() {
        const rail = document.getElementById('source-rail');
        rail.innerHTML = '';
        railSources().forEach((s) => {
            const card = document.createElement('div');
            card.className = 'card source-card';
            card.dataset.id = String(s.id);
            card.innerHTML =
                `<div>${s.placeType === 'RESTAURANT' ? '🍴' : '📍'} ${s.name}</div>` +
                `<div class="muted">${s.durationMinutes}분${s.reservationRequired ? ' · 🔔' : ''}</div>`;
            attachDrag(card, s.id);
            rail.appendChild(card);
        });
    }

    function renderDayNote() {
        document.getElementById('day-note-text').value = DAY_NOTES[selectedDate] || '';
    }

    async function saveDayNote() {
        const memo = document.getElementById('day-note-text').value;
        try {
            const res = await fetch(`/api/day-notes/${selectedDate}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ memo }),
            });
            if (isUnauthorized(res)) return;
            if (!res.ok) throw new Error('요청 실패');
            if (memo.trim() === '') delete DAY_NOTES[selectedDate];
            else DAY_NOTES[selectedDate] = memo;
        } catch (e) {
            alert('저장 실패, 다시 시도해주세요.');
        }
    }
```

- [ ] **Step 5: 지도와 시각 수정 시트를 채운다**

```js
    function renderMapForDay() {
        if (!map) return;
        if (dayPath) dayPath.setMap(null);
        const path = scheduledOn(selectedDate).map((s) => ({ lat: s.latitude, lng: s.longitude }));
        if (path.length < 2) { dayPath = null; return; }
        dayPath = new google.maps.Polyline({
            path, map, strokeOpacity: 0.8, strokeWeight: 3,
        });
    }

    function focusOnMap(source) {
        if (!map) return;
        map.panTo({ lat: source.latitude, lng: source.longitude });
    }

    // 드래그가 어려운 상황을 위한 폴백: 탭하면 시각을 직접 고른다.
    let sheetSourceId = null;

    function openTimeSheet(sourceId) {
        const s = SOURCES.find((v) => v.id === sourceId);
        sheetSourceId = sourceId;
        focusOnMap(s);

        document.getElementById('timeSheetTitle').textContent = s.name;

        const select = document.getElementById('timeSheetSelect');
        select.innerHTML = '';
        for (let m = TG.DAY_START; m <= TG.LAST_START; m += TG.SLOT) {
            const opt = document.createElement('option');
            opt.value = String(m);
            opt.textContent = TG.formatSlot(m);
            select.appendChild(opt);
        }
        select.value = String(s.startMinutes ?? 600);

        document.getElementById('timeSheetRemove').hidden = !s.scheduledDate;
        document.getElementById('timeSheet').classList.add('sheet--open');
        document.getElementById('timeSheetBackdrop').classList.add('sheet--open');
    }

    function closeTimeSheet() {
        document.getElementById('timeSheet').classList.remove('sheet--open');
        document.getElementById('timeSheetBackdrop').classList.remove('sheet--open');
        sheetSourceId = null;
    }

    function debounce(fn, wait) {
        let timeout;
        return (...args) => {
            clearTimeout(timeout);
            timeout = setTimeout(() => fn(...args), wait);
        };
    }

    function initMap() {
        map = new google.maps.Map(document.getElementById('map'), {
            center: { lat: SOURCES[0]?.latitude ?? 37.5665, lng: SOURCES[0]?.longitude ?? 126.9780 },
            zoom: 13,
            disableDefaultUI: true,
            zoomControl: true,
        });

        SOURCES.forEach((s) => {
            markers[s.id] = new google.maps.Marker({
                position: { lat: s.latitude, lng: s.longitude },
                map,
                title: s.name,
                icon: s.placeType === 'RESTAURANT'
                    ? { url: 'https://maps.google.com/mapfiles/ms/icons/red-dot.png' }
                    : undefined,
            });
        });

        map.addListener('bounds_changed', debounce(renderRail, 300));
        renderMapForDay();
    }

    window.initMap = initMap;
```

- [ ] **Step 6: 이벤트를 바인딩하고 첫 렌더를 한다**

`plan.js`의 IIFE 마지막에 넣는다.

```js
    document.getElementById('day-note-save').addEventListener('click', saveDayNote);

    document.getElementById('map-toggle').addEventListener('click', (e) => {
        const panel = document.getElementById('map-panel');
        panel.classList.toggle('collapsed');
        e.target.textContent = panel.classList.contains('collapsed') ? '지도 펼치기' : '지도 접기';
        updatePlannerHeight();
    });

    document.getElementById('timeSheetSave').addEventListener('click', async () => {
        const start = Number(document.getElementById('timeSheetSelect').value);
        const id = sheetSourceId;
        closeTimeSheet();
        await assign(id, selectedDate, start);
    });

    document.getElementById('timeSheetRemove').addEventListener('click', async () => {
        const id = sheetSourceId;
        closeTimeSheet();
        await unassign(id);
    });

    document.getElementById('timeSheetClose').addEventListener('click', closeTimeSheet);
    document.getElementById('timeSheetBackdrop').addEventListener('click', closeTimeSheet);

    // #planner 는 화면 나머지를 채워야 한다. 날짜 스트립·지도·참고사항 높이가
    // 바뀔 때마다 남는 높이를 CSS 변수로 알려준다.
    function updatePlannerHeight() {
        const used =
            document.getElementById('date-strip').offsetHeight +
            document.getElementById('map-panel').offsetHeight +
            document.getElementById('day-note').offsetHeight +
            56; // 하단 탭바
        document.documentElement.style.setProperty('--planner-offset', used + 'px');
    }

    window.addEventListener('resize', updatePlannerHeight);

    renderAll();
    updatePlannerHeight();
```

- [ ] **Step 7: 직접 확인한다**

Run: `./gradlew bootRun`, 브라우저 390px 모바일 에뮬레이션 + **터치 시뮬레이션 켜기**로 `http://localhost:8080/plan`

- [ ] 왼쪽 레일의 소스를 타임테이블로 드래그하면 스냅된 시각에 블록이 생긴다
- [ ] 드래그 중 반투명 미리보기 블록과 `10:30 – 12:00` 라벨이 보인다
- [ ] 드래그를 시작해도 페이지가 스크롤되지 않는다
- [ ] 타임테이블 위/아래 가장자리에서 자동 스크롤된다
- [ ] 배치된 블록을 다른 시각으로 옮길 수 있다
- [ ] 블록을 왼쪽 레일로 끌면 배정이 해제되고 레일에 다시 나온다
- [ ] 짧게 탭하면 드래그가 아니라 시각 수정 시트가 열린다
- [ ] 시트에서 시각 변경·배정 해제가 동작한다
- [ ] 겹치는 두 블록이 가로로 나뉘어 표시된다
- [ ] 소요시간이 길어 28:00을 넘기는 블록에 `28:00+`가 표시된다
- [ ] 날짜를 바꾸면 타임테이블·참고사항·지도 선이 함께 바뀐다
- [ ] 지도 접기/펴기가 동작하고 접으면 타임테이블이 늘어난다
- [ ] 지도를 움직이면 왼쪽 레일이 영역 안 소스로 걸러진다
- [ ] 다크모드에서 블록 글자가 읽힌다

- [ ] **Step 8: 커밋한다**

```bash
git add src/main/resources/templates/plan/index.html \
        src/main/resources/static/js/plan.js \
        src/main/resources/static/css/style.css
git commit -m "feat: 일자별 동선을 04~28시 타임테이블 드래그 배정 화면으로 재작성"
```

---

## Task 9: 계획 보기 화면

읽기 전용. 그 날 동선을 지도에 번호 순서대로 그리고, 아래에 시간순 카드로 보여준다.

**Files:**
- Create: `src/main/kotlin/com/juiceplan/dayview/DayViewController.kt`
- Create: `src/main/resources/templates/dayview/index.html`
- Create: `src/main/resources/static/js/dayview.js`
- Test: `src/test/kotlin/com/juiceplan/dayview/DayViewControllerIntegrationTest.kt`

**Interfaces:**
- Consumes: `TripService.current()`, `SourceService.list()`, `DayNoteService.allForRange(start, end)`, `window.TimeGrid` (Task 6), Task 4의 fragment
- Produces: `GET /day`

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`src/test/kotlin/com/juiceplan/dayview/DayViewControllerIntegrationTest.kt`

```kotlin
package com.juiceplan.dayview

import com.juiceplan.auth.SESSION_AUTHENTICATED_KEY
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DayViewControllerIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc

    private fun authedSession() = MockHttpSession().apply {
        setAttribute(SESSION_AUTHENTICATED_KEY, true)
    }

    @Test
    fun `renders the day view for an authenticated user`() {
        mockMvc.perform(get("/day").session(authedSession()))
            .andExpect(status().isOk)
            .andExpect(view().name("dayview/index"))
    }

    @Test
    fun `redirects an unauthenticated user to the login page`() {
        mockMvc.perform(get("/day"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/"))
    }
}
```

두 번째 테스트가 **Task 4에서 `WebConfig`에 `/day`를 추가한 것**을 검증한다. 빠뜨렸다면 여기서 200이 나오며 실패한다.

- [ ] **Step 2: 테스트가 실패하는지 확인한다**

Run: `./gradlew test --tests 'com.juiceplan.dayview.DayViewControllerIntegrationTest'`
Expected: 404로 FAIL

- [ ] **Step 3: 컨트롤러를 만든다**

```kotlin
package com.juiceplan.dayview

import com.juiceplan.daynote.DayNoteService
import com.juiceplan.source.SourceService
import com.juiceplan.trip.TripService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class DayViewController(
    private val sourceService: SourceService,
    private val tripService: TripService,
    private val dayNoteService: DayNoteService
) {
    @GetMapping("/day")
    fun index(model: Model): String {
        val trip = tripService.current()
        if (trip == null) {
            model.addAttribute("tripMissing", true)
            return "dayview/index"
        }
        model.addAttribute("tripMissing", false)
        model.addAttribute("trip", trip)
        model.addAttribute("sources", sourceService.list())
        model.addAttribute("dayNotes", dayNoteService.allForRange(trip.startDate, trip.endDate))
        return "dayview/index"
    }
}
```

- [ ] **Step 4: 템플릿을 만든다**

`src/main/resources/templates/dayview/index.html`

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragments/layout :: head('계획 보기')}"></head>
<body>
<div th:if="${tripMissing}" class="card">
    <p>먼저 여행 기간을 설정해주세요.</p>
    <a class="btn btn--primary" href="/sources">소스 관리로 이동</a>
</div>

<div th:unless="${tripMissing}" id="dayview-app"
     th:attr="data-trip-start=${trip.startDate}, data-trip-end=${trip.endDate}">
    <div class="date-strip" id="date-strip"></div>
    <div id="map"></div>
    <div id="itinerary"></div>
    <div id="day-note-view" class="card"></div>

    <script th:inline="javascript">
        /*<![CDATA[*/
        var SOURCES = /*[[${sources}]]*/ [];
        var DAY_NOTES = /*[[${dayNotes}]]*/ {};
        /*]]>*/
    </script>
</div>

<div th:replace="~{fragments/layout :: tabs('day')}"></div>

<script th:if="${!tripMissing}" th:src="@{/js/timegrid.js}"></script>
<script th:if="${!tripMissing}" th:src="@{/js/dayview.js}"></script>
<script th:if="${!tripMissing}"
        th:src="'https://maps.googleapis.com/maps/api/js?key=' + ${@environment.getProperty('app.google-maps-api-key')} + '&callback=initMap'"
        async defer></script>
</body>
</html>
```

`#map`에 높이를 준다 (`style.css`에 추가):

```css
#dayview-app #map { height: 40vh; width: 100%; }
```

- [ ] **Step 5: `dayview.js`를 만든다**

```js
(function () {
    const appEl = document.getElementById('dayview-app');
    const TG = window.TimeGrid;
    const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

    const days = [];
    {
        const cur = new Date(appEl.dataset.tripStart + 'T00:00:00');
        const end = new Date(appEl.dataset.tripEnd + 'T00:00:00');
        while (cur <= end) {
            const y = cur.getFullYear();
            const m = String(cur.getMonth() + 1).padStart(2, '0');
            const d = String(cur.getDate()).padStart(2, '0');
            days.push(`${y}-${m}-${d}`);
            cur.setDate(cur.getDate() + 1);
        }
    }

    let selectedDate = days[0];
    let map;
    let dayMarkers = [];
    let dayPath = null;

    function scheduledOn(date) {
        return SOURCES
            .filter((s) => s.scheduledDate === date && s.startMinutes != null)
            .sort((a, b) => a.startMinutes - b.startMinutes);
    }

    function renderDateStrip() {
        const strip = document.getElementById('date-strip');
        strip.innerHTML = '';
        days.forEach((date) => {
            const d = new Date(date + 'T00:00:00');
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'date-strip__item' + (date === selectedDate ? ' date-strip__item--on' : '');
            btn.innerHTML =
                `<span class="muted">${WEEKDAYS[d.getDay()]}</span><strong>${d.getDate()}</strong>`;
            btn.addEventListener('click', () => {
                selectedDate = date;
                renderAll();
            });
            strip.appendChild(btn);
        });
    }

    function renderItinerary() {
        const list = document.getElementById('itinerary');
        list.innerHTML = '';
        const items = scheduledOn(selectedDate);

        if (items.length === 0) {
            list.innerHTML = '<p class="card muted">이 날은 아직 일정이 없습니다.</p>';
            return;
        }

        items.forEach((s, i) => {
            const end = s.startMinutes + s.durationMinutes;
            const endLabel = end > TG.DAY_END ? '28:00+' : TG.formatSlot(end);
            const card = document.createElement('div');
            card.className = 'card';
            card.innerHTML =
                `<div><strong>${i + 1}. ${TG.formatSlot(s.startMinutes)}–${endLabel}</strong></div>` +
                `<div>${s.placeType === 'RESTAURANT' ? '🍴' : '📍'} ${s.name}</div>` +
                (s.reservationRequired ? `<div class="muted">🔔 예약 필요 (마감 ${s.reservationDeadline ?? '-'})</div>` : '') +
                (s.memo ? `<p class="muted">${s.memo}</p>` : '');
            list.appendChild(card);
        });
    }

    function renderDayNote() {
        const el = document.getElementById('day-note-view');
        const memo = DAY_NOTES[selectedDate];
        el.innerHTML = memo
            ? `<strong>참고사항</strong><p class="muted">${memo}</p>`
            : '<span class="muted">참고사항 없음</span>';
    }

    function renderMap() {
        if (!map) return;

        dayMarkers.forEach((m) => m.setMap(null));
        dayMarkers = [];
        if (dayPath) { dayPath.setMap(null); dayPath = null; }

        const items = scheduledOn(selectedDate);
        if (items.length === 0) return;

        const bounds = new google.maps.LatLngBounds();
        const path = [];

        items.forEach((s, i) => {
            const pos = { lat: s.latitude, lng: s.longitude };
            path.push(pos);
            bounds.extend(pos);
            dayMarkers.push(new google.maps.Marker({
                position: pos, map, label: String(i + 1), title: s.name,
            }));
        });

        if (path.length >= 2) {
            dayPath = new google.maps.Polyline({ path, map, strokeOpacity: 0.8, strokeWeight: 3 });
        }
        map.fitBounds(bounds);
    }

    function renderAll() {
        renderDateStrip();
        renderItinerary();
        renderDayNote();
        renderMap();
    }

    window.initMap = function () {
        map = new google.maps.Map(document.getElementById('map'), {
            center: { lat: SOURCES[0]?.latitude ?? 37.5665, lng: SOURCES[0]?.longitude ?? 126.9780 },
            zoom: 13,
            disableDefaultUI: true,
            zoomControl: true,
        });
        renderMap();
    };

    renderAll();
})();
```

- [ ] **Step 6: 테스트가 통과하는지 확인한다**

Run: `./gradlew test --tests 'com.juiceplan.dayview.DayViewControllerIntegrationTest'`
Expected: 2개 PASS

- [ ] **Step 7: 직접 확인한다**

Run: `./gradlew bootRun`, `http://localhost:8080/day`

- [ ] 그 날 동선이 지도에 번호 마커로 찍히고 순서대로 선으로 연결된다
- [ ] 지도가 그 날 동선에 맞게 자동으로 확대/이동한다
- [ ] 아래 카드가 시간순으로 번호와 함께 나온다
- [ ] 일정이 없는 날에는 "이 날은 아직 일정이 없습니다."가 나온다
- [ ] 참고사항이 보인다
- [ ] 하단 탭에서 계획 보기가 활성 표시된다

- [ ] **Step 8: 커밋한다**

```bash
git add src/main/kotlin/com/juiceplan/dayview/DayViewController.kt \
        src/main/resources/templates/dayview/index.html \
        src/main/resources/static/js/dayview.js \
        src/main/resources/static/css/style.css \
        src/test/kotlin/com/juiceplan/dayview/DayViewControllerIntegrationTest.kt
git commit -m "feat: 하루 일정을 지도와 함께 보는 계획 보기 화면 추가"
```

---

## Task 10: 마무리 검증

**Files:** 없음 (검증만)

- [ ] **Step 1: 전체 테스트를 돌린다**

Run: `./gradlew test`
Expected: 전부 PASS. 실패하면 고치고 다시 돌린다.

- [ ] **Step 2: 빈 DB로 처음부터 띄워본다**

마이그레이션이 `sort_order` 없는 새 DB에서도 안전한지 본다.

```bash
mv data data.bak
./gradlew bootRun
```

- [ ] 초기 설정 → 로그인 → 여행 기간 설정 → 소스 추가 → 타임테이블 배정까지 끊김 없이 된다
- [ ] 앱을 껐다 켜도 배정한 시각이 유지된다

확인 후 원래 DB로 되돌린다: `rm -rf data && mv data.bak data`

- [ ] **Step 3: 스펙의 수동 확인 체크리스트를 처음부터 훑는다**

`docs/superpowers/specs/2026-08-15-mobile-redesign-timetable-design.md`의 7절 체크리스트 14개 항목을 전부 확인한다.

- [ ] **Step 4: 커밋할 게 남았으면 커밋한다**

```bash
git status
```
