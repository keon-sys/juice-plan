# 여행 계획 앱 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Kotlin + Spring Boot 기반의 개인용 로컬 여행 계획 앱을 구현한다 — 비밀번호 게이트, 구글맵 링크로 장소(소스) 등록(비고 포함), 지도 기반 일자별 동선(타임테이블) 구성과 날짜별 참고사항 기록.

**Architecture:** 서버 렌더링(Thymeleaf) 위주 + 필요한 상호작용만 JSON API로 처리하는 하이브리드 구조. 세션 기반 자체 인증(스프링 시큐리티 미사용), 단일 Trip / 소스(Source) / 날짜별 참고사항(DayNote) 모델, H2 파일 DB.

**Tech Stack:** Kotlin, Spring Boot 3.3 (Web MVC, Thymeleaf, Data JPA, Validation), H2(파일 모드), spring-security-crypto(BCrypt), Gradle Kotlin DSL, JUnit5 + MockK + springmockk, Vanilla JS + Google Maps JavaScript API.

**Spec:** `docs/superpowers/specs/2026-08-13-travel-planner-design.md`

## Global Constraints

- 단일 사용자, 단일 여행(Trip)만 관리한다 — `Source`에 `tripId` FK를 두지 않는다.
- 인증은 세션 기반이며 브라우저 세션 동안만 유지한다 (별도 remember-me 쿠키 없음, 스프링 시큐리티 미사용).
- 비밀번호는 BCrypt로 해시하여 `AppSettings` 테이블에만 저장한다 — 실제 비밀번호 값은 소스코드/설정 파일 어디에도 하드코딩하지 않는다.
- DB는 H2 파일 모드(`jdbc:h2:file:./data/juice-plan`)를 사용하며, `data/` 디렉터리는 반드시 `.gitignore`에 포함해 커밋되지 않도록 한다 (개인정보/비밀번호 해시 보호).
- Google Maps API 키는 환경변수(`GOOGLE_MAPS_API_KEY`)로 주입하며 코드에 하드코딩하지 않는다.
- 모든 사용자 노출 텍스트(폼 라벨, 안내 메시지)는 한국어로 작성한다.
- 타임테이블 배정/해제/순서변경은 `Source.scheduledDate` + `Source.sortOrder` 두 필드만으로 표현한다 (별도 조인 테이블 없음).
- `POST /api/schedule/day/{date}`는 그 날짜에 대해 전달된 소스 id 배열을 authoritative하게 취급한다 — 배열에 없는 기존 배정은 자동으로 해제된다.
- `Source.memo`, `DayNote.memo`는 완전히 자유로운 선택 입력 텍스트이며 형식/길이 검증을 하지 않는다.
- `DayNote`는 참고사항이 실제로 있는 날짜에 대해서만 행을 가진다 — 빈 문자열 저장 시 해당 행을 삭제한다.
- 패키지 루트는 `com.juiceplan`이다.

---

### Task 1: 프로젝트 스캐폴딩

**Files:**
- Create: `build.gradle.kts`
- Create: `settings.gradle.kts`
- Create: `src/main/kotlin/com/juiceplan/JuicePlanApplication.kt`
- Create: `src/main/resources/application.yml`
- Create: `src/main/resources/application-test.yml`
- Create: `.gitignore`
- Test: `src/test/kotlin/com/juiceplan/JuicePlanApplicationTests.kt`

**Interfaces:**
- Consumes: 없음 (최초 작업)
- Produces: 부팅 가능한 Spring Boot 애플리케이션 컨텍스트, `app.google-maps-api-key` 설정 프로퍼티, `test` 프로파일

- [ ] **Step 1: 빌드 설정 작성**

`build.gradle.kts`:
```kotlin
plugins {
    id("org.springframework.boot") version "3.3.2"
    id("io.spring.dependency-management") version "1.1.6"
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.spring") version "1.9.24"
    kotlin("plugin.jpa") version "1.9.24"
}

group = "com.juiceplan"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.security:spring-security-crypto")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    runtimeOnly("com.h2database:h2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.mockk:mockk:1.13.11")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

`settings.gradle.kts`:
```kotlin
rootProject.name = "juice-plan"
```

`src/main/resources/application.yml`:
```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:h2:file:./data/juice-plan;AUTO_SERVER=TRUE
    driver-class-name: org.h2.Driver
    username: sa
    password: ""
  jpa:
    hibernate:
      ddl-auto: update
    open-in-view: false

app:
  google-maps-api-key: ${GOOGLE_MAPS_API_KEY:}
```

`src/main/resources/application-test.yml`:
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password: ""
  jpa:
    hibernate:
      ddl-auto: create-drop
```

`.gitignore`:
```
build/
.gradle/
data/
*.iml
.idea/
```

- [ ] **Step 2: Gradle wrapper 생성**

Run: `gradle wrapper --gradle-version 8.8` (시스템에 Gradle이 설치되어 있어야 합니다. IntelliJ에서 프로젝트를 열어 자동 생성해도 됩니다.)

- [ ] **Step 3: 메인 애플리케이션 클래스 작성**

`src/main/kotlin/com/juiceplan/JuicePlanApplication.kt`:
```kotlin
package com.juiceplan

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class JuicePlanApplication

fun main(args: Array<String>) {
    runApplication<JuicePlanApplication>(*args)
}
```

- [ ] **Step 4: 컨텍스트 로드 테스트 작성**

`src/test/kotlin/com/juiceplan/JuicePlanApplicationTests.kt`:
```kotlin
package com.juiceplan

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class JuicePlanApplicationTests {
    @Test
    fun contextLoads() {
    }
}
```

- [ ] **Step 5: 테스트 실행하여 통과 확인**

Run: `./gradlew test --tests "com.juiceplan.JuicePlanApplicationTests"`
Expected: PASS (컨텍스트가 정상적으로 로드됨)

- [ ] **Step 6: 커밋**

```bash
git add build.gradle.kts settings.gradle.kts gradlew gradlew.bat gradle/ \
  src/main/kotlin/com/juiceplan/JuicePlanApplication.kt \
  src/main/resources/application.yml src/main/resources/application-test.yml \
  src/test/kotlin/com/juiceplan/JuicePlanApplicationTests.kt .gitignore
git commit -m "chore: bootstrap Spring Boot Kotlin project"
```

---

### Task 2: 인증 도메인 — AppSettings / AuthService

**Files:**
- Create: `src/main/kotlin/com/juiceplan/auth/AppSettings.kt`
- Create: `src/main/kotlin/com/juiceplan/auth/AppSettingsRepository.kt`
- Create: `src/main/kotlin/com/juiceplan/auth/AuthService.kt`
- Test: `src/test/kotlin/com/juiceplan/auth/AuthServiceTest.kt`

**Interfaces:**
- Consumes: 없음
- Produces: `AppSettings(id: Long, passwordHash: String)`, `AppSettingsRepository : JpaRepository<AppSettings, Long>`, `AuthService(appSettingsRepository: AppSettingsRepository)` — `isConfigured(): Boolean`, `setInitialPassword(rawPassword: String)`, `verify(rawPassword: String): Boolean`

- [ ] **Step 1: 실패하는 단위 테스트 작성**

`src/test/kotlin/com/juiceplan/auth/AuthServiceTest.kt`:
```kotlin
package com.juiceplan.auth

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class AuthServiceTest {

    private val repository = mockk<AppSettingsRepository>()
    private val authService = AuthService(repository)

    @Test
    fun `isConfigured is false when no settings row exists`() {
        every { repository.count() } returns 0
        assertFalse(authService.isConfigured())
    }

    @Test
    fun `isConfigured is true when a settings row exists`() {
        every { repository.count() } returns 1
        assertTrue(authService.isConfigured())
    }

    @Test
    fun `setInitialPassword saves a bcrypt hash`() {
        every { repository.count() } returns 0
        every { repository.save(any()) } answers { firstArg() }

        authService.setInitialPassword("250707")

        verify {
            repository.save(match { it.passwordHash.startsWith("$2a$") || it.passwordHash.startsWith("$2b$") })
        }
    }

    @Test
    fun `setInitialPassword throws if already configured`() {
        every { repository.count() } returns 1
        assertThrows<IllegalStateException> {
            authService.setInitialPassword("250707")
        }
    }

    @Test
    fun `verify returns true for correct password`() {
        val encoder = BCryptPasswordEncoder()
        val settings = AppSettings(id = 1, passwordHash = encoder.encode("250707"))
        every { repository.findAll() } returns listOf(settings)

        assertTrue(authService.verify("250707"))
    }

    @Test
    fun `verify returns false for incorrect password`() {
        val encoder = BCryptPasswordEncoder()
        val settings = AppSettings(id = 1, passwordHash = encoder.encode("250707"))
        every { repository.findAll() } returns listOf(settings)

        assertFalse(authService.verify("wrong"))
    }

    @Test
    fun `verify returns false when unconfigured`() {
        every { repository.findAll() } returns emptyList()
        assertFalse(authService.verify("anything"))
    }
}
```

- [ ] **Step 2: 테스트 실행하여 컴파일 실패 확인**

Run: `./gradlew test --tests "com.juiceplan.auth.AuthServiceTest"`
Expected: FAIL (컴파일 오류 — `AppSettings`, `AppSettingsRepository`, `AuthService`가 아직 없음)

- [ ] **Step 3: 엔티티, 레포지토리, 서비스 구현**

`src/main/kotlin/com/juiceplan/auth/AppSettings.kt`:
```kotlin
package com.juiceplan.auth

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id

@Entity
class AppSettings(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    var passwordHash: String
)
```

`src/main/kotlin/com/juiceplan/auth/AppSettingsRepository.kt`:
```kotlin
package com.juiceplan.auth

import org.springframework.data.jpa.repository.JpaRepository

interface AppSettingsRepository : JpaRepository<AppSettings, Long>
```

`src/main/kotlin/com/juiceplan/auth/AuthService.kt`:
```kotlin
package com.juiceplan.auth

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val appSettingsRepository: AppSettingsRepository
) {
    private val passwordEncoder = BCryptPasswordEncoder()

    fun isConfigured(): Boolean = appSettingsRepository.count() > 0

    fun setInitialPassword(rawPassword: String) {
        check(!isConfigured()) { "Password already configured" }
        val hash = passwordEncoder.encode(rawPassword)
        appSettingsRepository.save(AppSettings(passwordHash = hash))
    }

    fun verify(rawPassword: String): Boolean {
        val settings = appSettingsRepository.findAll().firstOrNull() ?: return false
        return passwordEncoder.matches(rawPassword, settings.passwordHash)
    }
}
```

- [ ] **Step 4: 테스트 실행하여 통과 확인**

Run: `./gradlew test --tests "com.juiceplan.auth.AuthServiceTest"`
Expected: PASS (7개 테스트 모두 통과)

- [ ] **Step 5: 커밋**

```bash
git add src/main/kotlin/com/juiceplan/auth/ src/test/kotlin/com/juiceplan/auth/AuthServiceTest.kt
git commit -m "feat: add AppSettings entity and AuthService for password hashing"
```

---

### Task 3: 인증 웹 플로우 — 게이트 페이지, 인터셉터

**Files:**
- Create: `src/main/kotlin/com/juiceplan/auth/AuthController.kt`
- Create: `src/main/kotlin/com/juiceplan/auth/AuthInterceptor.kt`
- Create: `src/main/kotlin/com/juiceplan/config/WebConfig.kt`
- Create: `src/main/resources/templates/auth/setup.html`
- Create: `src/main/resources/templates/auth/login.html`
- Test: `src/test/kotlin/com/juiceplan/auth/AuthInterceptorTest.kt`
- Test: `src/test/kotlin/com/juiceplan/auth/AuthFlowIntegrationTest.kt`

**Interfaces:**
- Consumes: `AuthService` (Task 2)
- Produces: `SESSION_AUTHENTICATED_KEY: String` 상수, `AuthInterceptor`, `WebConfig`, `AuthController` (`GET /`, `POST /setup`, `POST /login`), 템플릿 `auth/setup`, `auth/login`

- [ ] **Step 1: 인터셉터 실패하는 단위 테스트 작성**

`src/test/kotlin/com/juiceplan/auth/AuthInterceptorTest.kt`:
```kotlin
package com.juiceplan.auth

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuthInterceptorTest {

    private val interceptor = AuthInterceptor()

    @Test
    fun `blocks and redirects when session not authenticated`() {
        val session = mockk<HttpSession>()
        every { session.getAttribute(SESSION_AUTHENTICATED_KEY) } returns null
        val request = mockk<HttpServletRequest>()
        every { request.session } returns session
        val response = mockk<HttpServletResponse>(relaxed = true)

        val result = interceptor.preHandle(request, response, Any())

        assertFalse(result)
        verify { response.sendRedirect("/") }
    }

    @Test
    fun `allows when session authenticated`() {
        val session = mockk<HttpSession>()
        every { session.getAttribute(SESSION_AUTHENTICATED_KEY) } returns true
        val request = mockk<HttpServletRequest>()
        every { request.session } returns session
        val response = mockk<HttpServletResponse>(relaxed = true)

        val result = interceptor.preHandle(request, response, Any())

        assertTrue(result)
    }
}
```

- [ ] **Step 2: 테스트 실행하여 컴파일 실패 확인**

Run: `./gradlew test --tests "com.juiceplan.auth.AuthInterceptorTest"`
Expected: FAIL (`AuthInterceptor`, `SESSION_AUTHENTICATED_KEY`가 아직 없음)

- [ ] **Step 3: 인터셉터와 WebConfig 구현**

`src/main/kotlin/com/juiceplan/auth/AuthInterceptor.kt`:
```kotlin
package com.juiceplan.auth

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

const val SESSION_AUTHENTICATED_KEY = "authenticated"

@Component
class AuthInterceptor : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val authenticated = request.session.getAttribute(SESSION_AUTHENTICATED_KEY) == true
        if (!authenticated) {
            response.sendRedirect("/")
            return false
        }
        return true
    }
}
```

`src/main/kotlin/com/juiceplan/config/WebConfig.kt`:
```kotlin
package com.juiceplan.config

import com.juiceplan.auth.AuthInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(private val authInterceptor: AuthInterceptor) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(authInterceptor)
            .addPathPatterns(
                "/sources", "/sources/**",
                "/plan", "/plan/**",
                "/trip/**",
                "/api/**"
            )
    }
}
```

- [ ] **Step 4: 인터셉터 테스트 통과 확인**

Run: `./gradlew test --tests "com.juiceplan.auth.AuthInterceptorTest"`
Expected: PASS

- [ ] **Step 5: 게이트/로그인 흐름 실패하는 통합 테스트 작성**

`src/test/kotlin/com/juiceplan/auth/AuthFlowIntegrationTest.kt`:
```kotlin
package com.juiceplan.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var appSettingsRepository: AppSettingsRepository

    @BeforeEach
    fun cleanUp() {
        appSettingsRepository.deleteAll()
    }

    @Test
    fun `shows setup page when no password configured`() {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(view().name("auth/setup"))
    }

    @Test
    fun `setting initial password authenticates and redirects to sources`() {
        val result = mockMvc.perform(
            post("/setup").param("password", "250707").param("passwordConfirm", "250707")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/sources"))
            .andReturn()

        val session = result.request.session
        assertEquals(true, session?.getAttribute(SESSION_AUTHENTICATED_KEY))
    }

    @Test
    fun `shows login page once password is configured`() {
        appSettingsRepository.save(AppSettings(passwordHash = BCryptPasswordEncoder().encode("250707")))

        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(view().name("auth/login"))
    }

    @Test
    fun `wrong password on login shows error`() {
        appSettingsRepository.save(AppSettings(passwordHash = BCryptPasswordEncoder().encode("250707")))

        mockMvc.perform(post("/login").param("password", "wrong"))
            .andExpect(status().isOk)
            .andExpect(view().name("auth/login"))
    }
}
```

- [ ] **Step 6: 테스트 실행하여 컴파일 실패 확인**

Run: `./gradlew test --tests "com.juiceplan.auth.AuthFlowIntegrationTest"`
Expected: FAIL (`AuthController`, 템플릿이 아직 없음)

- [ ] **Step 7: 컨트롤러와 템플릿 구현**

`src/main/kotlin/com/juiceplan/auth/AuthController.kt`:
```kotlin
package com.juiceplan.auth

import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.view.RedirectView

@Controller
class AuthController(private val authService: AuthService) {

    @GetMapping("/")
    fun gate(request: HttpServletRequest, model: Model): Any {
        if (request.session.getAttribute(SESSION_AUTHENTICATED_KEY) == true) {
            return RedirectView("/sources")
        }
        return if (authService.isConfigured()) "auth/login" else "auth/setup"
    }

    @PostMapping("/setup")
    fun setup(
        @RequestParam password: String,
        @RequestParam passwordConfirm: String,
        request: HttpServletRequest,
        model: Model
    ): Any {
        if (authService.isConfigured()) {
            return RedirectView("/")
        }
        if (password.isBlank() || password != passwordConfirm) {
            model.addAttribute("error", "비밀번호가 일치하지 않습니다.")
            return "auth/setup"
        }
        authService.setInitialPassword(password)
        request.session.setAttribute(SESSION_AUTHENTICATED_KEY, true)
        return RedirectView("/sources")
    }

    @PostMapping("/login")
    fun login(
        @RequestParam password: String,
        request: HttpServletRequest,
        model: Model
    ): Any {
        if (!authService.verify(password)) {
            model.addAttribute("error", "비밀번호가 올바르지 않습니다.")
            return "auth/login"
        }
        request.session.setAttribute(SESSION_AUTHENTICATED_KEY, true)
        return RedirectView("/sources")
    }
}
```

`src/main/resources/templates/auth/setup.html`:
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>초기 비밀번호 설정</title>
</head>
<body>
<h1>최초 비밀번호 설정</h1>
<p th:if="${error}" th:text="${error}" style="color:red;"></p>
<form method="post" th:action="@{/setup}">
    <label>비밀번호 <input type="password" name="password" required></label><br>
    <label>비밀번호 확인 <input type="password" name="passwordConfirm" required></label><br>
    <button type="submit">설정</button>
</form>
</body>
</html>
```

`src/main/resources/templates/auth/login.html`:
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>비밀번호 입력</title>
</head>
<body>
<h1>비밀번호 입력</h1>
<p th:if="${error}" th:text="${error}" style="color:red;"></p>
<form method="post" th:action="@{/login}">
    <label>비밀번호 <input type="password" name="password" required></label><br>
    <button type="submit">입장</button>
</form>
</body>
</html>
```

- [ ] **Step 8: 테스트 실행하여 통과 확인**

Run: `./gradlew test --tests "com.juiceplan.auth.*"`
Expected: PASS (모든 인증 테스트 통과)

- [ ] **Step 9: 커밋**

```bash
git add src/main/kotlin/com/juiceplan/auth/ src/main/kotlin/com/juiceplan/config/ \
  src/main/resources/templates/auth/ \
  src/test/kotlin/com/juiceplan/auth/AuthInterceptorTest.kt \
  src/test/kotlin/com/juiceplan/auth/AuthFlowIntegrationTest.kt
git commit -m "feat: add password gate flow with session-based auth interceptor"
```

---

### Task 4: Trip 도메인

**Files:**
- Create: `src/main/kotlin/com/juiceplan/trip/Trip.kt`
- Create: `src/main/kotlin/com/juiceplan/trip/TripRepository.kt`
- Create: `src/main/kotlin/com/juiceplan/trip/TripService.kt`
- Create: `src/main/kotlin/com/juiceplan/trip/TripController.kt`
- Test: `src/test/kotlin/com/juiceplan/trip/TripServiceTest.kt`
- Test: `src/test/kotlin/com/juiceplan/trip/TripControllerIntegrationTest.kt`

**Interfaces:**
- Consumes: `SESSION_AUTHENTICATED_KEY` (Task 3, 테스트에서만 사용)
- Produces: `Trip(id: Long, startDate: LocalDate, endDate: LocalDate)`, `TripRepository`, `TripService.current(): Trip?`, `TripService.save(startDate: LocalDate, endDate: LocalDate): Trip`, `TripController` (`POST /trip`, `/sources`로 리다이렉트)

- [ ] **Step 1: 실패하는 서비스 단위 테스트 작성**

`src/test/kotlin/com/juiceplan/trip/TripServiceTest.kt`:
```kotlin
package com.juiceplan.trip

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class TripServiceTest {

    private val repository = mockk<TripRepository>()
    private val service = TripService(repository)

    @Test
    fun `creates a new trip when none exists`() {
        every { repository.findAll() } returns emptyList()
        every { repository.save(any()) } answers { firstArg() }

        val trip = service.save(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5))

        assertEquals(LocalDate.of(2026, 9, 1), trip.startDate)
        assertEquals(LocalDate.of(2026, 9, 5), trip.endDate)
    }

    @Test
    fun `updates the existing trip instead of creating a second one`() {
        val existing = Trip(id = 1, startDate = LocalDate.of(2026, 1, 1), endDate = LocalDate.of(2026, 1, 2))
        every { repository.findAll() } returns listOf(existing)
        every { repository.save(any()) } answers { firstArg() }

        val trip = service.save(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5))

        assertEquals(1L, trip.id)
        assertEquals(LocalDate.of(2026, 9, 1), trip.startDate)
    }

    @Test
    fun `rejects start date after end date`() {
        every { repository.findAll() } returns emptyList()
        assertThrows<IllegalArgumentException> {
            service.save(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 1))
        }
    }
}
```

- [ ] **Step 2: 테스트 실행하여 컴파일 실패 확인**

Run: `./gradlew test --tests "com.juiceplan.trip.TripServiceTest"`
Expected: FAIL

- [ ] **Step 3: 엔티티, 레포지토리, 서비스 구현**

`src/main/kotlin/com/juiceplan/trip/Trip.kt`:
```kotlin
package com.juiceplan.trip

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.time.LocalDate

@Entity
class Trip(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    var startDate: LocalDate,
    var endDate: LocalDate
)
```

`src/main/kotlin/com/juiceplan/trip/TripRepository.kt`:
```kotlin
package com.juiceplan.trip

import org.springframework.data.jpa.repository.JpaRepository

interface TripRepository : JpaRepository<Trip, Long>
```

`src/main/kotlin/com/juiceplan/trip/TripService.kt`:
```kotlin
package com.juiceplan.trip

import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class TripService(private val tripRepository: TripRepository) {

    fun current(): Trip? = tripRepository.findAll().firstOrNull()

    fun save(startDate: LocalDate, endDate: LocalDate): Trip {
        require(!startDate.isAfter(endDate)) { "시작일은 종료일보다 늦을 수 없습니다." }
        val existing = current()
        val trip = if (existing != null) {
            existing.startDate = startDate
            existing.endDate = endDate
            existing
        } else {
            Trip(startDate = startDate, endDate = endDate)
        }
        return tripRepository.save(trip)
    }
}
```

- [ ] **Step 4: 서비스 테스트 통과 확인**

Run: `./gradlew test --tests "com.juiceplan.trip.TripServiceTest"`
Expected: PASS

- [ ] **Step 5: 실패하는 컨트롤러 통합 테스트 작성**

`src/test/kotlin/com/juiceplan/trip/TripControllerIntegrationTest.kt`:
```kotlin
package com.juiceplan.trip

import com.juiceplan.auth.SESSION_AUTHENTICATED_KEY
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TripControllerIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var tripRepository: TripRepository

    @Test
    fun `saving trip dates redirects to sources`() {
        val session = MockHttpSession()
        session.setAttribute(SESSION_AUTHENTICATED_KEY, true)

        mockMvc.perform(
            post("/trip").session(session)
                .param("startDate", "2026-09-01")
                .param("endDate", "2026-09-05")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/sources"))

        val trip = tripRepository.findAll().first()
        assertEquals(LocalDate.of(2026, 9, 1), trip.startDate)
    }

    @Test
    fun `unauthenticated request is redirected to gate`() {
        mockMvc.perform(
            post("/trip")
                .param("startDate", "2026-09-01")
                .param("endDate", "2026-09-05")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/"))
    }
}
```

- [ ] **Step 6: 테스트 실행하여 컴파일 실패 확인**

Run: `./gradlew test --tests "com.juiceplan.trip.TripControllerIntegrationTest"`
Expected: FAIL (`TripController`가 아직 없음)

- [ ] **Step 7: 컨트롤러 구현**

`src/main/kotlin/com/juiceplan/trip/TripController.kt`:
```kotlin
package com.juiceplan.trip

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.view.RedirectView
import java.time.LocalDate

@Controller
class TripController(private val tripService: TripService) {

    @PostMapping("/trip")
    fun save(
        @RequestParam startDate: String,
        @RequestParam endDate: String
    ): RedirectView {
        tripService.save(LocalDate.parse(startDate), LocalDate.parse(endDate))
        return RedirectView("/sources")
    }
}
```

- [ ] **Step 8: 테스트 실행하여 통과 확인**

Run: `./gradlew test --tests "com.juiceplan.trip.*"`
Expected: PASS

- [ ] **Step 9: 커밋**

```bash
git add src/main/kotlin/com/juiceplan/trip/ src/test/kotlin/com/juiceplan/trip/
git commit -m "feat: add Trip entity, service, and controller for trip date range"
```

---

### Task 5: 구글맵 링크 파서

**Files:**
- Create: `src/main/kotlin/com/juiceplan/source/UrlResolver.kt`
- Create: `src/main/kotlin/com/juiceplan/source/HttpUrlResolver.kt`
- Create: `src/main/kotlin/com/juiceplan/source/GoogleMapsLinkParser.kt`
- Create: `src/main/kotlin/com/juiceplan/source/GoogleMapsSourceLinkService.kt`
- Test: `src/test/kotlin/com/juiceplan/source/GoogleMapsLinkParserTest.kt`
- Test: `src/test/kotlin/com/juiceplan/source/GoogleMapsSourceLinkServiceTest.kt`

**Interfaces:**
- Consumes: 없음
- Produces: `ParsedPlace(name: String?, latitude: Double, longitude: Double)`, `UrlResolver.resolve(shortUrl: String): String`, `HttpUrlResolver`, `GoogleMapsLinkParser.parse(resolvedUrl: String): ParsedPlace?`, `LinkParseResult(success: Boolean, place: ParsedPlace?)`, `GoogleMapsSourceLinkService.parseLink(shortUrl: String): LinkParseResult`

- [ ] **Step 1: 파서 실패하는 단위 테스트 작성 (네트워크 불필요, 순수 문자열 파싱)**

`src/test/kotlin/com/juiceplan/source/GoogleMapsLinkParserTest.kt`:
```kotlin
package com.juiceplan.source

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GoogleMapsLinkParserTest {

    private val parser = GoogleMapsLinkParser()

    @Test
    fun `extracts name and coordinates from a standard place url`() {
        val url = "https://www.google.com/maps/place/Gyeongbokgung+Palace/@37.5796,126.9770,17z/data=xyz"

        val result = parser.parse(url)

        assertEquals("Gyeongbokgung Palace", result?.name)
        assertEquals(37.5796, result?.latitude)
        assertEquals(126.9770, result?.longitude)
    }

    @Test
    fun `returns coordinates even when name segment is absent`() {
        val url = "https://www.google.com/maps/@37.5796,126.9770,17z"

        val result = parser.parse(url)

        assertNull(result?.name)
        assertEquals(37.5796, result?.latitude)
    }

    @Test
    fun `returns null when url has no coordinate pattern`() {
        val url = "https://www.google.com/maps/search/restaurants+near+me"

        val result = parser.parse(url)

        assertNull(result)
    }
}
```

- [ ] **Step 2: 테스트 실행하여 컴파일 실패 확인**

Run: `./gradlew test --tests "com.juiceplan.source.GoogleMapsLinkParserTest"`
Expected: FAIL

- [ ] **Step 3: 파서 구현**

`src/main/kotlin/com/juiceplan/source/GoogleMapsLinkParser.kt`:
```kotlin
package com.juiceplan.source

import org.springframework.stereotype.Component
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class ParsedPlace(val name: String?, val latitude: Double, val longitude: Double)

@Component
class GoogleMapsLinkParser {

    private val coordPattern = Regex("""@(-?\d+\.\d+),(-?\d+\.\d+),\d+(?:\.\d+)?z""")
    private val namePattern = Regex("""/place/([^/@]+)""")

    fun parse(resolvedUrl: String): ParsedPlace? {
        val coordMatch = coordPattern.find(resolvedUrl) ?: return null
        val latitude = coordMatch.groupValues[1].toDouble()
        val longitude = coordMatch.groupValues[2].toDouble()

        val name = namePattern.find(resolvedUrl)?.groupValues?.get(1)?.let {
            URLDecoder.decode(it, StandardCharsets.UTF_8).replace('+', ' ')
        }

        return ParsedPlace(name = name, latitude = latitude, longitude = longitude)
    }
}
```

- [ ] **Step 4: 파서 테스트 통과 확인**

Run: `./gradlew test --tests "com.juiceplan.source.GoogleMapsLinkParserTest"`
Expected: PASS

- [ ] **Step 5: 링크 서비스 실패하는 단위 테스트 작성**

`src/test/kotlin/com/juiceplan/source/GoogleMapsSourceLinkServiceTest.kt`:
```kotlin
package com.juiceplan.source

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class GoogleMapsSourceLinkServiceTest {

    private val resolver = mockk<UrlResolver>()
    private val parser = GoogleMapsLinkParser()
    private val service = GoogleMapsSourceLinkService(resolver, parser)

    @Test
    fun `returns success with parsed place when resolution and parsing succeed`() {
        every { resolver.resolve("https://maps.app.goo.gl/abc") } returns
            "https://www.google.com/maps/place/Gyeongbokgung+Palace/@37.5796,126.9770,17z/data=xyz"

        val result = service.parseLink("https://maps.app.goo.gl/abc")

        assertTrue(result.success)
        assertEquals(37.5796, result.place?.latitude)
    }

    @Test
    fun `returns failure when resolver throws`() {
        every { resolver.resolve(any()) } throws IOException("network error")

        val result = service.parseLink("https://maps.app.goo.gl/broken")

        assertFalse(result.success)
    }

    @Test
    fun `returns failure when resolved url has no coordinates`() {
        every { resolver.resolve(any()) } returns "https://www.google.com/maps/search/restaurants"

        val result = service.parseLink("https://maps.app.goo.gl/search-link")

        assertFalse(result.success)
    }
}
```

- [ ] **Step 6: 테스트 실행하여 컴파일 실패 확인**

Run: `./gradlew test --tests "com.juiceplan.source.GoogleMapsSourceLinkServiceTest"`
Expected: FAIL

- [ ] **Step 7: UrlResolver와 GoogleMapsSourceLinkService 구현**

`src/main/kotlin/com/juiceplan/source/UrlResolver.kt`:
```kotlin
package com.juiceplan.source

interface UrlResolver {
    fun resolve(shortUrl: String): String
}
```

`src/main/kotlin/com/juiceplan/source/HttpUrlResolver.kt`:
```kotlin
package com.juiceplan.source

import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Component
class HttpUrlResolver : UrlResolver {

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    override fun resolve(shortUrl: String): String {
        val request = HttpRequest.newBuilder(URI.create(shortUrl))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.discarding())
        return response.uri().toString()
    }
}
```

`src/main/kotlin/com/juiceplan/source/GoogleMapsSourceLinkService.kt`:
```kotlin
package com.juiceplan.source

import org.springframework.stereotype.Service

data class LinkParseResult(val success: Boolean, val place: ParsedPlace? = null)

@Service
class GoogleMapsSourceLinkService(
    private val urlResolver: UrlResolver,
    private val linkParser: GoogleMapsLinkParser
) {
    fun parseLink(shortUrl: String): LinkParseResult {
        val resolved = try {
            urlResolver.resolve(shortUrl)
        } catch (ex: Exception) {
            return LinkParseResult(success = false)
        }
        val place = linkParser.parse(resolved) ?: return LinkParseResult(success = false)
        return LinkParseResult(success = true, place = place)
    }
}
```

- [ ] **Step 8: 테스트 실행하여 통과 확인**

Run: `./gradlew test --tests "com.juiceplan.source.*"`
Expected: PASS

- [ ] **Step 9: 커밋**

```bash
git add src/main/kotlin/com/juiceplan/source/UrlResolver.kt \
  src/main/kotlin/com/juiceplan/source/HttpUrlResolver.kt \
  src/main/kotlin/com/juiceplan/source/GoogleMapsLinkParser.kt \
  src/main/kotlin/com/juiceplan/source/GoogleMapsSourceLinkService.kt \
  src/test/kotlin/com/juiceplan/source/GoogleMapsLinkParserTest.kt \
  src/test/kotlin/com/juiceplan/source/GoogleMapsSourceLinkServiceTest.kt
git commit -m "feat: add Google Maps share link resolution and parsing"
```

---

### Task 6: Source 도메인 (엔티티, 서비스) — 비고(memo) 포함

**Files:**
- Create: `src/main/kotlin/com/juiceplan/source/Source.kt`
- Create: `src/main/kotlin/com/juiceplan/source/SourceRepository.kt`
- Create: `src/main/kotlin/com/juiceplan/source/SourceService.kt`
- Test: `src/test/kotlin/com/juiceplan/source/SourceServiceTest.kt`

**Interfaces:**
- Consumes: 없음
- Produces: `PlaceType` enum(`RESTAURANT`, `ATTRACTION`), `Source` 엔티티(`id, googleMapsUrl, name, latitude, longitude, placeType, durationMinutes, reservationRequired, reservationDeadline, memo, scheduledDate, sortOrder`), `SourceRepository.findByScheduledDate(date: LocalDate): List<Source>`, `SourceInput`(memo 포함), `SourceService.list()/get(id)/create(input)/update(id, input)/delete(id)`

- [ ] **Step 1: 실패하는 서비스 단위 테스트 작성**

`src/test/kotlin/com/juiceplan/source/SourceServiceTest.kt`:
```kotlin
package com.juiceplan.source

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.util.Optional

class SourceServiceTest {

    private val repository = mockk<SourceRepository>()
    private val service = SourceService(repository)

    private fun validInput(
        reservationRequired: Boolean = false,
        deadline: LocalDate? = null,
        memo: String? = null
    ) = SourceInput(
        googleMapsUrl = "https://maps.app.goo.gl/abc",
        name = "경복궁",
        latitude = 37.5796,
        longitude = 126.9770,
        placeType = PlaceType.ATTRACTION,
        durationHours = 1,
        durationMinutesPart = 30,
        reservationRequired = reservationRequired,
        reservationDeadline = deadline,
        memo = memo
    )

    @Test
    fun `create converts hours and minutes into total minutes`() {
        every { repository.save(any()) } answers { firstArg() }

        val source = service.create(validInput())

        assertEquals(90, source.durationMinutes)
    }

    @Test
    fun `create rejects reservation required without a deadline`() {
        assertThrows<IllegalArgumentException> {
            service.create(validInput(reservationRequired = true, deadline = null))
        }
    }

    @Test
    fun `create accepts reservation required with a deadline`() {
        every { repository.save(any()) } answers { firstArg() }

        val source = service.create(validInput(reservationRequired = true, deadline = LocalDate.of(2026, 8, 1)))

        assertEquals(LocalDate.of(2026, 8, 1), source.reservationDeadline)
    }

    @Test
    fun `create persists an optional memo`() {
        every { repository.save(any()) } answers { firstArg() }

        val source = service.create(validInput(memo = "창가 자리 요청"))

        assertEquals("창가 자리 요청", source.memo)
    }

    @Test
    fun `create leaves memo null when not provided`() {
        every { repository.save(any()) } answers { firstArg() }

        val source = service.create(validInput())

        assertNull(source.memo)
    }

    @Test
    fun `delete removes the source by id`() {
        every { repository.deleteById(5L) } returns Unit

        service.delete(5L)

        verify { repository.deleteById(5L) }
    }

    @Test
    fun `get throws when source does not exist`() {
        every { repository.findById(99L) } returns Optional.empty()

        assertThrows<NoSuchElementException> { service.get(99L) }
    }
}
```

- [ ] **Step 2: 테스트 실행하여 컴파일 실패 확인**

Run: `./gradlew test --tests "com.juiceplan.source.SourceServiceTest"`
Expected: FAIL

- [ ] **Step 3: 엔티티, 레포지토리, 서비스 구현**

`src/main/kotlin/com/juiceplan/source/Source.kt`:
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

    var scheduledDate: LocalDate? = null,
    var sortOrder: Int = 0
)
```

`src/main/kotlin/com/juiceplan/source/SourceRepository.kt`:
```kotlin
package com.juiceplan.source

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface SourceRepository : JpaRepository<Source, Long> {
    fun findByScheduledDate(scheduledDate: LocalDate): List<Source>
}
```

`src/main/kotlin/com/juiceplan/source/SourceService.kt`:
```kotlin
package com.juiceplan.source

import org.springframework.stereotype.Service
import java.time.LocalDate

data class SourceInput(
    val googleMapsUrl: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val placeType: PlaceType,
    val durationHours: Int,
    val durationMinutesPart: Int,
    val reservationRequired: Boolean,
    val reservationDeadline: LocalDate?,
    val memo: String?
)

@Service
class SourceService(private val sourceRepository: SourceRepository) {

    fun list(): List<Source> = sourceRepository.findAll()

    fun get(id: Long): Source = sourceRepository.findById(id)
        .orElseThrow { NoSuchElementException("소스를 찾을 수 없습니다: $id") }

    fun create(input: SourceInput): Source {
        validate(input)
        val source = Source(
            googleMapsUrl = input.googleMapsUrl,
            name = input.name,
            latitude = input.latitude,
            longitude = input.longitude,
            placeType = input.placeType,
            durationMinutes = toDurationMinutes(input.durationHours, input.durationMinutesPart),
            reservationRequired = input.reservationRequired,
            reservationDeadline = input.reservationDeadline,
            memo = input.memo?.ifBlank { null }
        )
        return sourceRepository.save(source)
    }

    fun update(id: Long, input: SourceInput): Source {
        validate(input)
        val source = get(id)
        source.googleMapsUrl = input.googleMapsUrl
        source.name = input.name
        source.latitude = input.latitude
        source.longitude = input.longitude
        source.placeType = input.placeType
        source.durationMinutes = toDurationMinutes(input.durationHours, input.durationMinutesPart)
        source.reservationRequired = input.reservationRequired
        source.reservationDeadline = input.reservationDeadline
        source.memo = input.memo?.ifBlank { null }
        return sourceRepository.save(source)
    }

    fun delete(id: Long) {
        sourceRepository.deleteById(id)
    }

    private fun toDurationMinutes(hours: Int, minutes: Int): Int = hours * 60 + minutes

    private fun validate(input: SourceInput) {
        require(!(input.reservationRequired && input.reservationDeadline == null)) {
            "예약이 필요한 경우 예약 마감일을 입력해야 합니다."
        }
    }
}
```

- [ ] **Step 4: 테스트 실행하여 통과 확인**

Run: `./gradlew test --tests "com.juiceplan.source.SourceServiceTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/kotlin/com/juiceplan/source/Source.kt \
  src/main/kotlin/com/juiceplan/source/SourceRepository.kt \
  src/main/kotlin/com/juiceplan/source/SourceService.kt \
  src/test/kotlin/com/juiceplan/source/SourceServiceTest.kt
git commit -m "feat: add Source entity with optional memo and CRUD service"
```

---

### Task 7: 페이지1 — 소스 관리 웹 레이어 (비고 입력 포함)

**Files:**
- Create: `src/main/kotlin/com/juiceplan/source/SourceController.kt`
- Create: `src/main/kotlin/com/juiceplan/source/SourceLinkController.kt`
- Create: `src/main/resources/templates/fragments/layout.html`
- Create: `src/main/resources/templates/sources/index.html`
- Create: `src/main/resources/static/js/sources.js`
- Create: `src/main/resources/static/css/style.css`
- Test: `src/test/kotlin/com/juiceplan/source/SourceControllerIntegrationTest.kt`

**Interfaces:**
- Consumes: `SourceService`(Task 6), `TripService`(Task 4), `GoogleMapsSourceLinkService`(Task 5), `SESSION_AUTHENTICATED_KEY`(Task 3, 테스트용)
- Produces: `SourceController`(`GET/POST /sources`, `PUT/DELETE /sources/{id}`), `SourceForm`(memo 포함), `SourceLinkController`(`POST /api/sources/parse-link`), 템플릿 `fragments/layout :: tabs(active)` (Task 10 페이지2에서도 재사용), `sources/index`

- [ ] **Step 1: 실패하는 컨트롤러 통합 테스트 작성**

`src/test/kotlin/com/juiceplan/source/SourceControllerIntegrationTest.kt`:
```kotlin
package com.juiceplan.source

import com.juiceplan.auth.SESSION_AUTHENTICATED_KEY
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.io.IOException

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SourceControllerIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var sourceRepository: SourceRepository

    @MockkBean
    lateinit var urlResolver: UrlResolver

    private lateinit var session: MockHttpSession

    @BeforeEach
    fun setUp() {
        sourceRepository.deleteAll()
        session = MockHttpSession()
        session.setAttribute(SESSION_AUTHENTICATED_KEY, true)
    }

    @Test
    fun `creating a source persists it with memo and redirects to sources list`() {
        mockMvc.perform(
            post("/sources").session(session)
                .param("googleMapsUrl", "https://maps.app.goo.gl/abc")
                .param("name", "경복궁")
                .param("latitude", "37.5796")
                .param("longitude", "126.9770")
                .param("placeType", "ATTRACTION")
                .param("durationHours", "1")
                .param("durationMinutesPart", "30")
                .param("reservationRequired", "false")
                .param("memo", "창가 자리 요청")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/sources"))

        val saved = sourceRepository.findAll()
        assertEquals(1, saved.size)
        assertEquals(90, saved[0].durationMinutes)
        assertEquals("창가 자리 요청", saved[0].memo)
    }

    @Test
    fun `deleting a source removes it`() {
        val source = sourceRepository.save(
            Source(
                googleMapsUrl = "https://maps.app.goo.gl/abc",
                name = "경복궁",
                latitude = 37.5796,
                longitude = 126.9770,
                placeType = PlaceType.ATTRACTION,
                durationMinutes = 90,
                reservationRequired = false
            )
        )

        mockMvc.perform(delete("/sources/${source.id}").session(session))
            .andExpect(status().is3xxRedirection)

        assertTrue(sourceRepository.findAll().isEmpty())
    }

    @Test
    fun `parse-link returns parsed place on success`() {
        every { urlResolver.resolve(any()) } returns
            "https://www.google.com/maps/place/Gyeongbokgung+Palace/@37.5796,126.9770,17z/data=xyz"

        mockMvc.perform(
            post("/api/sources/parse-link").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"url":"https://maps.app.goo.gl/abc"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.place.latitude").value(37.5796))
    }

    @Test
    fun `parse-link returns failure when resolver throws`() {
        every { urlResolver.resolve(any()) } throws IOException("boom")

        mockMvc.perform(
            post("/api/sources/parse-link").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"url":"https://maps.app.goo.gl/broken"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
    }

    @Test
    fun `sources page lists saved sources`() {
        sourceRepository.save(
            Source(
                googleMapsUrl = "https://maps.app.goo.gl/abc",
                name = "경복궁",
                latitude = 37.5796,
                longitude = 126.9770,
                placeType = PlaceType.ATTRACTION,
                durationMinutes = 90,
                reservationRequired = false
            )
        )

        mockMvc.perform(get("/sources").session(session))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("경복궁")))
    }

    @Test
    fun `unauthenticated access to sources page redirects to gate`() {
        mockMvc.perform(get("/sources"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/"))
    }
}
```

- [ ] **Step 2: 테스트 실행하여 컴파일 실패 확인**

Run: `./gradlew test --tests "com.juiceplan.source.SourceControllerIntegrationTest"`
Expected: FAIL (`SourceController`, `SourceLinkController`, 템플릿이 아직 없음)

- [ ] **Step 3: 컨트롤러 구현**

`src/main/kotlin/com/juiceplan/source/SourceController.kt`:
```kotlin
package com.juiceplan.source

import com.juiceplan.trip.TripService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.servlet.view.RedirectView
import java.time.LocalDate

@Controller
class SourceController(
    private val sourceService: SourceService,
    private val tripService: TripService
) {

    @GetMapping("/sources")
    fun index(model: Model): String {
        model.addAttribute("sources", sourceService.list())
        model.addAttribute("trip", tripService.current())
        model.addAttribute("placeTypes", PlaceType.entries)
        return "sources/index"
    }

    @PostMapping("/sources")
    fun create(@ModelAttribute form: SourceForm): RedirectView {
        sourceService.create(form.toInput())
        return RedirectView("/sources")
    }

    @PutMapping("/sources/{id}")
    fun update(@PathVariable id: Long, @ModelAttribute form: SourceForm): RedirectView {
        sourceService.update(id, form.toInput())
        return RedirectView("/sources")
    }

    @DeleteMapping("/sources/{id}")
    fun delete(@PathVariable id: Long): RedirectView {
        sourceService.delete(id)
        return RedirectView("/sources")
    }
}

data class SourceForm(
    val googleMapsUrl: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val placeType: PlaceType,
    val durationHours: Int,
    val durationMinutesPart: Int,
    val reservationRequired: Boolean,
    val reservationDeadline: LocalDate?,
    val memo: String?
) {
    fun toInput() = SourceInput(
        googleMapsUrl = googleMapsUrl,
        name = name,
        latitude = latitude,
        longitude = longitude,
        placeType = placeType,
        durationHours = durationHours,
        durationMinutesPart = durationMinutesPart,
        reservationRequired = reservationRequired,
        reservationDeadline = reservationDeadline,
        memo = memo
    )
}
```

`src/main/kotlin/com/juiceplan/source/SourceLinkController.kt`:
```kotlin
package com.juiceplan.source

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

data class ParseLinkRequest(val url: String)

@RestController
class SourceLinkController(private val linkService: GoogleMapsSourceLinkService) {

    @PostMapping("/api/sources/parse-link")
    fun parseLink(@RequestBody request: ParseLinkRequest): LinkParseResult {
        return linkService.parseLink(request.url)
    }
}
```

- [ ] **Step 4: 공통 레이아웃(풋터 탭) 프래그먼트 작성**

`src/main/resources/templates/fragments/layout.html`:
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body>
<footer th:fragment="tabs(active)">
    <nav>
        <a href="/sources" th:classappend="${active == 'sources'} ? 'active' : ''">소스 관리</a>
        <a href="/plan" th:classappend="${active == 'plan'} ? 'active' : ''">일자별 동선</a>
    </nav>
</footer>
</body>
</html>
```

- [ ] **Step 5: 소스 관리 페이지 템플릿 작성 (비고 입력/표시 포함)**

`src/main/resources/templates/sources/index.html`:
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>소스 관리</title>
    <link rel="stylesheet" th:href="@{/css/style.css}">
</head>
<body>
<h1>소스 관리</h1>

<section id="trip-widget">
    <div th:if="${trip == null}">
        <form method="post" th:action="@{/trip}">
            <label>시작일 <input type="date" name="startDate" required></label>
            <label>종료일 <input type="date" name="endDate" required></label>
            <button type="submit">여행 기간 설정</button>
        </form>
    </div>
    <div th:if="${trip != null}">
        <span th:text="'여행 기간: ' + ${trip.startDate} + ' ~ ' + ${trip.endDate}"></span>
        <a href="#" onclick="document.getElementById('trip-edit').style.display='block'; return false;">수정</a>
        <form id="trip-edit" method="post" th:action="@{/trip}" style="display:none;">
            <input type="date" name="startDate" th:value="${trip.startDate}" required>
            <input type="date" name="endDate" th:value="${trip.endDate}" required>
            <button type="submit">저장</button>
        </form>
    </div>
</section>

<section id="source-form">
    <h2>새 소스 추가</h2>
    <form method="post" th:action="@{/sources}" id="sourceForm">
        <label>구글맵 공유 링크
            <input type="text" id="googleMapsUrl" name="googleMapsUrl" required>
            <button type="button" id="parseLinkBtn">가져오기</button>
        </label>
        <p id="parseError" style="color:red; display:none;"></p>

        <label>이름 <input type="text" id="name" name="name" required></label>
        <label>위도 <input type="number" step="any" id="latitude" name="latitude" required></label>
        <label>경도 <input type="number" step="any" id="longitude" name="longitude" required></label>

        <fieldset>
            <legend>장소 종류</legend>
            <label><input type="radio" name="placeType" value="RESTAURANT" required> 음식점</label>
            <label><input type="radio" name="placeType" value="ATTRACTION"> 관광지</label>
        </fieldset>

        <label>예상 소요 시간
            <input type="number" min="0" name="durationHours" value="0"> 시간
            <input type="number" min="0" max="59" name="durationMinutesPart" value="0"> 분
        </label>

        <label><input type="checkbox" id="reservationRequired" name="reservationRequired" value="true"> 예약 필요</label>
        <div id="reservationDeadlineWrap" style="display:none;">
            <label>예약 마감일 <input type="date" name="reservationDeadline"></label>
        </div>

        <label>비고 <textarea name="memo" rows="2" placeholder="예: 창가 자리 요청, 현금만 가능"></textarea></label>

        <button type="submit">저장</button>
    </form>
</section>

<section id="source-list">
    <h2>등록된 소스</h2>
    <ul>
        <li th:each="s : ${sources}">
            <span th:text="${s.name}"></span>
            (<span th:text="${s.placeType}"></span>,
            <span th:text="${s.durationMinutes} + '분'"></span>)
            <span th:text="${s.scheduledDate != null} ? ${s.scheduledDate} : '미배정'"></span>
            <p th:if="${s.memo != null}" th:text="${s.memo}" class="memo"></p>
            <button type="button" class="delete-btn" th:attr="data-id=${s.id}">삭제</button>
        </li>
    </ul>
</section>

<div th:replace="~{fragments/layout :: tabs('sources')}"></div>

<script th:src="@{/js/sources.js}"></script>
</body>
</html>
```

- [ ] **Step 6: JS와 CSS 작성**

`src/main/resources/static/js/sources.js`:
```javascript
document.getElementById('parseLinkBtn').addEventListener('click', async () => {
    const url = document.getElementById('googleMapsUrl').value;
    const errorEl = document.getElementById('parseError');
    errorEl.style.display = 'none';

    try {
        const res = await fetch('/api/sources/parse-link', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ url }),
        });
        const data = await res.json();

        if (!data.success) {
            errorEl.textContent = '링크에서 위치 정보를 찾을 수 없습니다. 이름과 위치를 직접 입력해주세요.';
            errorEl.style.display = 'block';
            return;
        }

        if (data.place.name) {
            document.getElementById('name').value = data.place.name;
        }
        document.getElementById('latitude').value = data.place.latitude;
        document.getElementById('longitude').value = data.place.longitude;
    } catch (e) {
        errorEl.textContent = '요청 중 오류가 발생했습니다. 이름과 위치를 직접 입력해주세요.';
        errorEl.style.display = 'block';
    }
});

document.getElementById('reservationRequired').addEventListener('change', (e) => {
    document.getElementById('reservationDeadlineWrap').style.display = e.target.checked ? 'block' : 'none';
});

document.querySelectorAll('.delete-btn').forEach((btn) => {
    btn.addEventListener('click', async () => {
        const id = btn.getAttribute('data-id');
        if (!confirm('삭제하시겠습니까?')) return;
        await fetch(`/sources/${id}`, { method: 'DELETE' });
        window.location.reload();
    });
});
```

`src/main/resources/static/css/style.css`:
```css
body { font-family: sans-serif; padding-bottom: 60px; }
footer { position: fixed; bottom: 0; left: 0; right: 0; background: #fff; border-top: 1px solid #ddd; }
footer nav { display: flex; }
footer nav a { flex: 1; text-align: center; padding: 12px; text-decoration: none; color: #333; }
footer nav a.active { font-weight: bold; color: #1a73e8; }
#map { height: 400px; width: 100%; }
.source-card, .timetable-item { padding: 8px; border: 1px solid #ddd; margin: 4px 0; cursor: pointer; }
.memo { color: #666; font-size: 0.9em; white-space: pre-wrap; }
#day-note textarea { width: 100%; }
```

- [ ] **Step 7: 테스트 실행하여 통과 확인**

Run: `./gradlew test --tests "com.juiceplan.source.*"`
Expected: PASS

- [ ] **Step 8: 커밋**

```bash
git add src/main/kotlin/com/juiceplan/source/SourceController.kt \
  src/main/kotlin/com/juiceplan/source/SourceLinkController.kt \
  src/main/resources/templates/fragments/layout.html \
  src/main/resources/templates/sources/index.html \
  src/main/resources/static/ \
  src/test/kotlin/com/juiceplan/source/SourceControllerIntegrationTest.kt
git commit -m "feat: add sources page with memo field and footer tab navigation"
```

---

### Task 8: 스케줄 배정/해제 기능

**Files:**
- Create: `src/main/kotlin/com/juiceplan/schedule/ScheduleService.kt`
- Create: `src/main/kotlin/com/juiceplan/schedule/ScheduleController.kt`
- Test: `src/test/kotlin/com/juiceplan/schedule/ScheduleServiceTest.kt`
- Test: `src/test/kotlin/com/juiceplan/schedule/ScheduleControllerIntegrationTest.kt`

**Interfaces:**
- Consumes: `Source`, `SourceRepository`(Task 6), `SESSION_AUTHENTICATED_KEY`(Task 3, 테스트용)
- Produces: `ScheduleService.assignDay(date: LocalDate, orderedSourceIds: List<Long>)`, `ScheduleService.remove(sourceId: Long)`, `ScheduleController`(`POST /api/schedule/day/{date}`, `DELETE /api/schedule/{sourceId}`), `AssignDayRequest`

- [ ] **Step 1: 실패하는 서비스 테스트 작성 (실제 JPA dirty-checking 검증을 위해 `@DataJpaTest` 사용)**

`src/test/kotlin/com/juiceplan/schedule/ScheduleServiceTest.kt`:
```kotlin
package com.juiceplan.schedule

import com.juiceplan.source.PlaceType
import com.juiceplan.source.Source
import com.juiceplan.source.SourceRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
    fun `assignDay sets scheduledDate and sequential sortOrder`() {
        val a = newSource("A")
        val b = newSource("B")
        val date = LocalDate.of(2026, 9, 1)

        scheduleService.assignDay(date, listOf(a.id, b.id))

        val reloadedA = sourceRepository.findById(a.id).get()
        val reloadedB = sourceRepository.findById(b.id).get()
        assertEquals(date, reloadedA.scheduledDate)
        assertEquals(0, reloadedA.sortOrder)
        assertEquals(date, reloadedB.scheduledDate)
        assertEquals(1, reloadedB.sortOrder)
    }

    @Test
    fun `assignDay unassigns sources previously on that day but missing from the new list`() {
        val a = newSource("A")
        val b = newSource("B")
        val date = LocalDate.of(2026, 9, 1)
        scheduleService.assignDay(date, listOf(a.id, b.id))

        scheduleService.assignDay(date, listOf(a.id))

        val reloadedB = sourceRepository.findById(b.id).get()
        assertNull(reloadedB.scheduledDate)
    }

    @Test
    fun `remove clears scheduledDate`() {
        val a = newSource("A")
        val date = LocalDate.of(2026, 9, 1)
        scheduleService.assignDay(date, listOf(a.id))

        scheduleService.remove(a.id)

        val reloaded = sourceRepository.findById(a.id).get()
        assertNull(reloaded.scheduledDate)
        assertEquals(0, reloaded.sortOrder)
    }
}
```

- [ ] **Step 2: 테스트 실행하여 컴파일 실패 확인**

Run: `./gradlew test --tests "com.juiceplan.schedule.ScheduleServiceTest"`
Expected: FAIL

- [ ] **Step 3: 서비스 구현**

`src/main/kotlin/com/juiceplan/schedule/ScheduleService.kt`:
```kotlin
package com.juiceplan.schedule

import com.juiceplan.source.SourceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class ScheduleService(private val sourceRepository: SourceRepository) {

    @Transactional
    fun assignDay(date: LocalDate, orderedSourceIds: List<Long>) {
        val newIdSet = orderedSourceIds.toSet()

        sourceRepository.findByScheduledDate(date)
            .filter { it.id !in newIdSet }
            .forEach {
                it.scheduledDate = null
                it.sortOrder = 0
            }

        orderedSourceIds.forEachIndexed { index, id ->
            val source = sourceRepository.findById(id)
                .orElseThrow { NoSuchElementException("소스를 찾을 수 없습니다: $id") }
            source.scheduledDate = date
            source.sortOrder = index
        }
    }

    @Transactional
    fun remove(sourceId: Long) {
        val source = sourceRepository.findById(sourceId)
            .orElseThrow { NoSuchElementException("소스를 찾을 수 없습니다: $sourceId") }
        source.scheduledDate = null
        source.sortOrder = 0
    }
}
```

- [ ] **Step 4: 테스트 실행하여 통과 확인**

Run: `./gradlew test --tests "com.juiceplan.schedule.ScheduleServiceTest"`
Expected: PASS

- [ ] **Step 5: 실패하는 컨트롤러 통합 테스트 작성**

`src/test/kotlin/com/juiceplan/schedule/ScheduleControllerIntegrationTest.kt`:
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
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
    fun `assigns sources to a day`() {
        val a = newSource("A")

        mockMvc.perform(
            post("/api/schedule/day/2026-09-01").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"sourceIds":[${a.id}]}""")
        ).andExpect(status().isOk)

        val reloaded = sourceRepository.findById(a.id).get()
        assertEquals(LocalDate.of(2026, 9, 1), reloaded.scheduledDate)
    }

    @Test
    fun `removes a source from schedule`() {
        val a = newSource("A")
        a.scheduledDate = LocalDate.of(2026, 9, 1)
        sourceRepository.save(a)

        mockMvc.perform(delete("/api/schedule/${a.id}").session(session))
            .andExpect(status().isOk)

        val reloaded = sourceRepository.findById(a.id).get()
        assertNull(reloaded.scheduledDate)
    }

    @Test
    fun `unauthenticated request is blocked`() {
        mockMvc.perform(
            post("/api/schedule/day/2026-09-01")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"sourceIds":[]}""")
        ).andExpect(status().is3xxRedirection)
    }
}
```

- [ ] **Step 6: 테스트 실행하여 컴파일 실패 확인**

Run: `./gradlew test --tests "com.juiceplan.schedule.ScheduleControllerIntegrationTest"`
Expected: FAIL (`ScheduleController`가 아직 없음)

- [ ] **Step 7: 컨트롤러 구현**

`src/main/kotlin/com/juiceplan/schedule/ScheduleController.kt`:
```kotlin
package com.juiceplan.schedule

import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

data class AssignDayRequest(val sourceIds: List<Long>)

@RestController
class ScheduleController(private val scheduleService: ScheduleService) {

    @PostMapping("/api/schedule/day/{date}")
    fun assignDay(@PathVariable date: String, @RequestBody request: AssignDayRequest) {
        scheduleService.assignDay(LocalDate.parse(date), request.sourceIds)
    }

    @DeleteMapping("/api/schedule/{sourceId}")
    fun remove(@PathVariable sourceId: Long) {
        scheduleService.remove(sourceId)
    }
}
```

- [ ] **Step 8: 테스트 실행하여 통과 확인**

Run: `./gradlew test --tests "com.juiceplan.schedule.*"`
Expected: PASS

- [ ] **Step 9: 커밋**

```bash
git add src/main/kotlin/com/juiceplan/schedule/ src/test/kotlin/com/juiceplan/schedule/
git commit -m "feat: add schedule assignment API with authoritative day reassignment"
```

---

### Task 9: 날짜별 참고사항(DayNote) 도메인

**Files:**
- Create: `src/main/kotlin/com/juiceplan/daynote/DayNote.kt`
- Create: `src/main/kotlin/com/juiceplan/daynote/DayNoteRepository.kt`
- Create: `src/main/kotlin/com/juiceplan/daynote/DayNoteService.kt`
- Create: `src/main/kotlin/com/juiceplan/daynote/DayNoteController.kt`
- Test: `src/test/kotlin/com/juiceplan/daynote/DayNoteServiceTest.kt`
- Test: `src/test/kotlin/com/juiceplan/daynote/DayNoteControllerIntegrationTest.kt`

**Interfaces:**
- Consumes: `SESSION_AUTHENTICATED_KEY`(Task 3, 테스트용)
- Produces: `DayNote(id, date: LocalDate, memo: String)`, `DayNoteRepository.findByDate(date): DayNote?` / `findByDateBetween(start, end): List<DayNote>`, `DayNoteService.allForRange(startDate, endDate): Map<LocalDate, String>`, `DayNoteService.save(date, memo)` (빈 문자열이면 삭제), `DayNoteController`(`POST /api/day-notes/{date}`)

- [ ] **Step 1: 실패하는 서비스 테스트 작성 (`@DataJpaTest`로 실제 upsert/삭제 동작 검증)**

`src/test/kotlin/com/juiceplan/daynote/DayNoteServiceTest.kt`:
```kotlin
package com.juiceplan.daynote

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import java.time.LocalDate

@DataJpaTest
@Import(DayNoteService::class)
class DayNoteServiceTest {

    @Autowired lateinit var dayNoteRepository: DayNoteRepository
    @Autowired lateinit var dayNoteService: DayNoteService

    @Test
    fun `save creates a new note when none exists for the date`() {
        val date = LocalDate.of(2026, 9, 1)

        dayNoteService.save(date, "오전엔 우천 예보")

        assertEquals("오전엔 우천 예보", dayNoteRepository.findByDate(date)?.memo)
    }

    @Test
    fun `save updates the existing note instead of creating a duplicate`() {
        val date = LocalDate.of(2026, 9, 1)
        dayNoteService.save(date, "첫 메모")

        dayNoteService.save(date, "수정된 메모")

        val notes = dayNoteRepository.findByDateBetween(date, date)
        assertEquals(1, notes.size)
        assertEquals("수정된 메모", notes[0].memo)
    }

    @Test
    fun `save with blank memo deletes the existing note`() {
        val date = LocalDate.of(2026, 9, 1)
        dayNoteService.save(date, "지울 메모")

        dayNoteService.save(date, "")

        assertNull(dayNoteRepository.findByDate(date))
    }

    @Test
    fun `allForRange returns a map keyed by date for dates within range`() {
        dayNoteService.save(LocalDate.of(2026, 9, 1), "1일차 메모")
        dayNoteService.save(LocalDate.of(2026, 9, 3), "3일차 메모")
        dayNoteService.save(LocalDate.of(2026, 9, 10), "범위 밖 메모")

        val result = dayNoteService.allForRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5))

        assertEquals(2, result.size)
        assertEquals("1일차 메모", result[LocalDate.of(2026, 9, 1)])
        assertEquals("3일차 메모", result[LocalDate.of(2026, 9, 3)])
    }
}
```

- [ ] **Step 2: 테스트 실행하여 컴파일 실패 확인**

Run: `./gradlew test --tests "com.juiceplan.daynote.DayNoteServiceTest"`
Expected: FAIL

- [ ] **Step 3: 엔티티, 레포지토리, 서비스 구현**

`src/main/kotlin/com/juiceplan/daynote/DayNote.kt`:
```kotlin
package com.juiceplan.daynote

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.time.LocalDate

@Entity
class DayNote(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(unique = true)
    var date: LocalDate,

    var memo: String
)
```

`src/main/kotlin/com/juiceplan/daynote/DayNoteRepository.kt`:
```kotlin
package com.juiceplan.daynote

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface DayNoteRepository : JpaRepository<DayNote, Long> {
    fun findByDate(date: LocalDate): DayNote?
    fun findByDateBetween(start: LocalDate, end: LocalDate): List<DayNote>
}
```

`src/main/kotlin/com/juiceplan/daynote/DayNoteService.kt`:
```kotlin
package com.juiceplan.daynote

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class DayNoteService(private val dayNoteRepository: DayNoteRepository) {

    fun allForRange(startDate: LocalDate, endDate: LocalDate): Map<LocalDate, String> =
        dayNoteRepository.findByDateBetween(startDate, endDate).associate { it.date to it.memo }

    @Transactional
    fun save(date: LocalDate, memo: String) {
        val existing = dayNoteRepository.findByDate(date)
        if (memo.isBlank()) {
            existing?.let { dayNoteRepository.delete(it) }
            return
        }
        if (existing != null) {
            existing.memo = memo
        } else {
            dayNoteRepository.save(DayNote(date = date, memo = memo))
        }
    }
}
```

- [ ] **Step 4: 테스트 실행하여 통과 확인**

Run: `./gradlew test --tests "com.juiceplan.daynote.DayNoteServiceTest"`
Expected: PASS

- [ ] **Step 5: 실패하는 컨트롤러 통합 테스트 작성**

`src/test/kotlin/com/juiceplan/daynote/DayNoteControllerIntegrationTest.kt`:
```kotlin
package com.juiceplan.daynote

import com.juiceplan.auth.SESSION_AUTHENTICATED_KEY
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DayNoteControllerIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var dayNoteRepository: DayNoteRepository

    private lateinit var session: MockHttpSession

    @BeforeEach
    fun setUp() {
        dayNoteRepository.deleteAll()
        session = MockHttpSession()
        session.setAttribute(SESSION_AUTHENTICATED_KEY, true)
    }

    @Test
    fun `saves a memo for the given date`() {
        mockMvc.perform(
            post("/api/day-notes/2026-09-01").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"memo":"오전엔 우천 예보"}""")
        ).andExpect(status().isOk)

        val note = dayNoteRepository.findByDate(LocalDate.of(2026, 9, 1))
        assertEquals("오전엔 우천 예보", note?.memo)
    }

    @Test
    fun `saving a blank memo deletes the existing note`() {
        dayNoteRepository.save(DayNote(date = LocalDate.of(2026, 9, 1), memo = "지울 메모"))

        mockMvc.perform(
            post("/api/day-notes/2026-09-01").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"memo":""}""")
        ).andExpect(status().isOk)

        assertNull(dayNoteRepository.findByDate(LocalDate.of(2026, 9, 1)))
    }

    @Test
    fun `unauthenticated request is blocked`() {
        mockMvc.perform(
            post("/api/day-notes/2026-09-01")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"memo":"test"}""")
        ).andExpect(status().is3xxRedirection)
    }
}
```

- [ ] **Step 6: 테스트 실행하여 컴파일 실패 확인**

Run: `./gradlew test --tests "com.juiceplan.daynote.DayNoteControllerIntegrationTest"`
Expected: FAIL (`DayNoteController`가 아직 없음)

- [ ] **Step 7: 컨트롤러 구현**

`src/main/kotlin/com/juiceplan/daynote/DayNoteController.kt`:
```kotlin
package com.juiceplan.daynote

import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

data class DayNoteRequest(val memo: String)

@RestController
class DayNoteController(private val dayNoteService: DayNoteService) {

    @PostMapping("/api/day-notes/{date}")
    fun save(@PathVariable date: String, @RequestBody request: DayNoteRequest) {
        dayNoteService.save(LocalDate.parse(date), request.memo)
    }
}
```

- [ ] **Step 8: 테스트 실행하여 통과 확인**

Run: `./gradlew test --tests "com.juiceplan.daynote.*"`
Expected: PASS

- [ ] **Step 9: 커밋**

```bash
git add src/main/kotlin/com/juiceplan/daynote/ src/test/kotlin/com/juiceplan/daynote/
git commit -m "feat: add DayNote domain for per-date trip notes"
```

---

### Task 10: 페이지2 — 일자별 동선 컨트롤러/템플릿 (날짜별 참고사항 임베드)

**Files:**
- Create: `src/main/kotlin/com/juiceplan/plan/PlanController.kt`
- Create: `src/main/resources/templates/plan/index.html`
- Test: `src/test/kotlin/com/juiceplan/plan/PlanControllerIntegrationTest.kt`

**Interfaces:**
- Consumes: `SourceService`(Task 6), `TripService`(Task 4), `DayNoteService`(Task 9), `fragments/layout :: tabs(active)`(Task 7)
- Produces: `PlanController`(`GET /plan`), `plan/index` 템플릿 — `#plan-app` 엘리먼트(`data-trip-start`, `data-trip-end` 속성), 임베드된 JS 전역 `SOURCES` 배열과 `DAY_NOTES` 객체(`{"2026-09-01":"메모", ...}`, Task 11에서 소비)

- [ ] **Step 1: 실패하는 컨트롤러 통합 테스트 작성**

`src/test/kotlin/com/juiceplan/plan/PlanControllerIntegrationTest.kt`:
```kotlin
package com.juiceplan.plan

import com.juiceplan.auth.SESSION_AUTHENTICATED_KEY
import com.juiceplan.daynote.DayNoteRepository
import com.juiceplan.daynote.DayNote
import com.juiceplan.trip.Trip
import com.juiceplan.trip.TripRepository
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanControllerIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var tripRepository: TripRepository
    @Autowired lateinit var dayNoteRepository: DayNoteRepository

    private lateinit var session: MockHttpSession

    @BeforeEach
    fun setUp() {
        tripRepository.deleteAll()
        dayNoteRepository.deleteAll()
        session = MockHttpSession()
        session.setAttribute(SESSION_AUTHENTICATED_KEY, true)
    }

    @Test
    fun `shows guidance message when trip is not configured`() {
        mockMvc.perform(get("/plan").session(session))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("먼저 여행 기간을 설정해주세요")))
    }

    @Test
    fun `renders plan page with embedded sources and day notes when trip exists`() {
        tripRepository.save(Trip(startDate = LocalDate.of(2026, 9, 1), endDate = LocalDate.of(2026, 9, 5)))
        dayNoteRepository.save(DayNote(date = LocalDate.of(2026, 9, 1), memo = "오전엔 우천 예보"))

        mockMvc.perform(get("/plan").session(session))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("plan-app")))
            .andExpect(content().string(containsString("오전엔 우천 예보")))
    }

    @Test
    fun `unauthenticated access redirects to gate`() {
        mockMvc.perform(get("/plan"))
            .andExpect(status().is3xxRedirection)
    }
}
```

- [ ] **Step 2: 테스트 실행하여 컴파일 실패 확인**

Run: `./gradlew test --tests "com.juiceplan.plan.PlanControllerIntegrationTest"`
Expected: FAIL (`PlanController`가 아직 없음)

- [ ] **Step 3: 컨트롤러 구현**

`src/main/kotlin/com/juiceplan/plan/PlanController.kt`:
```kotlin
package com.juiceplan.plan

import com.juiceplan.daynote.DayNoteService
import com.juiceplan.source.SourceService
import com.juiceplan.trip.TripService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class PlanController(
    private val sourceService: SourceService,
    private val tripService: TripService,
    private val dayNoteService: DayNoteService
) {
    @GetMapping("/plan")
    fun index(model: Model): String {
        val trip = tripService.current()
        if (trip == null) {
            model.addAttribute("tripMissing", true)
            return "plan/index"
        }
        model.addAttribute("trip", trip)
        model.addAttribute("sources", sourceService.list())
        model.addAttribute("dayNotes", dayNoteService.allForRange(trip.startDate, trip.endDate))
        return "plan/index"
    }
}
```

- [ ] **Step 4: 템플릿 작성 (날짜별 참고사항 영역 포함)**

`src/main/resources/templates/plan/index.html`:
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>일자별 동선</title>
    <link rel="stylesheet" th:href="@{/css/style.css}">
</head>
<body>
<div th:if="${tripMissing}">
    <p>먼저 여행 기간을 설정해주세요.</p>
    <a href="/sources">소스 관리로 이동</a>
</div>

<div th:unless="${tripMissing}" id="plan-app"
     th:attr="data-trip-start=${trip.startDate}, data-trip-end=${trip.endDate}">
    <div id="date-tabs"></div>
    <div id="day-note">
        <textarea id="day-note-text" rows="2" placeholder="이 날짜의 참고사항을 입력하세요"></textarea>
        <button type="button" id="day-note-save">참고사항 저장</button>
    </div>
    <div id="available-list"></div>
    <div id="map"></div>
    <div id="timetable"></div>

    <script th:inline="javascript">
        /*<![CDATA[*/
        var SOURCES = /*[[${sources}]]*/ [];
        var DAY_NOTES = /*[[${dayNotes}]]*/ {};
        /*]]>*/
    </script>
</div>

<div th:replace="~{fragments/layout :: tabs('plan')}"></div>

<script th:if="${!tripMissing}" th:src="@{/js/plan.js}"></script>
<script th:if="${!tripMissing}"
        th:src="'https://maps.googleapis.com/maps/api/js?key=' + ${@environment.getProperty('app.google-maps-api-key')} + '&callback=initMap'"
        async defer></script>
</body>
</html>
```

- [ ] **Step 5: 테스트 실행하여 통과 확인**

Run: `./gradlew test --tests "com.juiceplan.plan.*"`
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add src/main/kotlin/com/juiceplan/plan/ src/main/resources/templates/plan/ \
  src/test/kotlin/com/juiceplan/plan/
git commit -m "feat: add plan page controller with embedded sources and day notes"
```

---

### Task 11: 페이지2 — 지도/목록/타임테이블/참고사항 JS 상호작용

**Files:**
- Create: `src/main/resources/static/js/plan.js`

**Interfaces:**
- Consumes: `plan/index.html`의 `#plan-app`(`data-trip-start`, `data-trip-end`), 전역 `SOURCES` 배열과 `DAY_NOTES` 객체(Task 10), `POST /api/schedule/day/{date}` / `DELETE /api/schedule/{sourceId}`(Task 8), `POST /api/day-notes/{date}`(Task 9)
- Produces: 지도 초기화(`window.initMap`), 뷰포트 필터링, 클릭 강조, 드래그앤드롭 배정/해제, 날짜별 참고사항 편집/저장

이 작업은 브라우저 상호작용(지도, 드래그앤드롭)이 핵심이라 스펙에 명시된 대로 자동화 테스트 대상이 아니며, 실제 브라우저로 수동 검증한다.

- [ ] **Step 1: plan.js 구현**

`src/main/resources/static/js/plan.js`:
```javascript
(function () {
    const appEl = document.getElementById('plan-app');
    const tripStart = new Date(appEl.dataset.tripStart);
    const tripEnd = new Date(appEl.dataset.tripEnd);

    const days = [];
    for (let d = new Date(tripStart); d <= tripEnd; d.setDate(d.getDate() + 1)) {
        days.push(new Date(d).toISOString().slice(0, 10));
    }

    let selectedDate = days[0];
    let map;
    const markers = {};

    function renderDateTabs() {
        const container = document.getElementById('date-tabs');
        container.innerHTML = '';
        days.forEach((date) => {
            const btn = document.createElement('button');
            btn.textContent = date;
            btn.classList.toggle('active', date === selectedDate);
            btn.addEventListener('click', () => {
                selectedDate = date;
                renderDateTabs();
                renderTimetable();
                renderDayNote();
            });
            container.appendChild(btn);
        });
    }

    function renderDayNote() {
        const textarea = document.getElementById('day-note-text');
        textarea.value = DAY_NOTES[selectedDate] || '';
    }

    async function saveDayNote() {
        const textarea = document.getElementById('day-note-text');
        const memo = textarea.value;
        try {
            const res = await fetch(`/api/day-notes/${selectedDate}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ memo }),
            });
            if (!res.ok) throw new Error('요청 실패');
            if (memo.trim() === '') {
                delete DAY_NOTES[selectedDate];
            } else {
                DAY_NOTES[selectedDate] = memo;
            }
        } catch (e) {
            alert('저장 실패, 다시 시도해주세요.');
        }
    }

    function availableSources() {
        const unscheduled = SOURCES.filter((s) => !s.scheduledDate);
        if (!map) return unscheduled;
        const bounds = map.getBounds();
        if (!bounds) return unscheduled;
        return unscheduled.filter((s) => bounds.contains({ lat: s.latitude, lng: s.longitude }));
    }

    function renderAvailableList() {
        const list = document.getElementById('available-list');
        list.innerHTML = '';
        availableSources().forEach((s) => {
            const item = document.createElement('div');
            item.className = 'source-card';
            item.textContent = s.name;
            item.draggable = true;
            item.dataset.id = s.id;
            item.addEventListener('click', () => focusOnMap(s));
            item.addEventListener('dragstart', (e) => {
                e.dataTransfer.setData('text/plain', String(s.id));
            });
            list.appendChild(item);
        });

        list.addEventListener('dragover', (e) => e.preventDefault());
        list.addEventListener('drop', (e) => {
            e.preventDefault();
            const id = Number(e.dataTransfer.getData('text/plain'));
            removeFromSchedule(id);
        });
    }

    function timetableSources() {
        return SOURCES
            .filter((s) => s.scheduledDate === selectedDate)
            .sort((a, b) => a.sortOrder - b.sortOrder);
    }

    function renderTimetable() {
        const container = document.getElementById('timetable');
        container.innerHTML = '';
        timetableSources().forEach((s) => {
            const item = document.createElement('div');
            item.className = 'timetable-item';
            item.draggable = true;
            item.dataset.id = s.id;

            const label = document.createElement('span');
            label.textContent = s.name;
            item.appendChild(label);

            const removeBtn = document.createElement('button');
            removeBtn.textContent = 'X';
            removeBtn.addEventListener('click', () => removeFromSchedule(s.id));
            item.appendChild(removeBtn);

            item.addEventListener('click', () => focusOnMap(s));
            item.addEventListener('dragstart', (e) => {
                e.dataTransfer.setData('text/plain', String(s.id));
            });

            container.appendChild(item);
        });

        container.addEventListener('dragover', (e) => e.preventDefault());
        container.addEventListener('drop', (e) => {
            e.preventDefault();
            const id = Number(e.dataTransfer.getData('text/plain'));
            addToSchedule(id);
        });
    }

    function focusOnMap(source) {
        if (!map) return;
        map.panTo({ lat: source.latitude, lng: source.longitude });
        const marker = markers[source.id];
        if (marker) {
            marker.setAnimation(google.maps.Animation.BOUNCE);
            setTimeout(() => marker.setAnimation(null), 1400);
        }
    }

    async function addToSchedule(sourceId) {
        const currentIds = timetableSources().map((s) => s.id);
        if (currentIds.includes(sourceId)) return;
        await saveDay([...currentIds, sourceId]);
    }

    async function removeFromSchedule(sourceId) {
        try {
            const res = await fetch(`/api/schedule/${sourceId}`, { method: 'DELETE' });
            if (!res.ok) throw new Error('요청 실패');
            const source = SOURCES.find((s) => s.id === sourceId);
            source.scheduledDate = null;
            source.sortOrder = 0;
            renderAvailableList();
            renderTimetable();
        } catch (e) {
            alert('저장 실패, 다시 시도해주세요.');
        }
    }

    async function saveDay(orderedIds) {
        try {
            const res = await fetch(`/api/schedule/day/${selectedDate}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ sourceIds: orderedIds }),
            });
            if (!res.ok) throw new Error('요청 실패');

            orderedIds.forEach((id, index) => {
                const source = SOURCES.find((s) => s.id === id);
                source.scheduledDate = selectedDate;
                source.sortOrder = index;
            });
            SOURCES.forEach((s) => {
                if (s.scheduledDate === selectedDate && !orderedIds.includes(s.id)) {
                    s.scheduledDate = null;
                    s.sortOrder = 0;
                }
            });

            renderAvailableList();
            renderTimetable();
        } catch (e) {
            alert('저장 실패, 다시 시도해주세요.');
        }
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
        });

        SOURCES.forEach((s) => {
            const marker = new google.maps.Marker({
                position: { lat: s.latitude, lng: s.longitude },
                map,
                title: s.name,
                icon: s.placeType === 'RESTAURANT'
                    ? { url: 'https://maps.google.com/mapfiles/ms/icons/red-dot.png' }
                    : undefined,
            });
            markers[s.id] = marker;
        });

        map.addListener('bounds_changed', debounce(renderAvailableList, 300));
    }

    window.initMap = initMap;

    document.getElementById('day-note-save').addEventListener('click', saveDayNote);

    renderDateTabs();
    renderDayNote();
    renderAvailableList();
    renderTimetable();
})();
```

- [ ] **Step 2: 앱을 실행하여 수동 검증**

Run: `GOOGLE_MAPS_API_KEY=<발급받은 키> ./gradlew bootRun`

브라우저에서 `http://localhost:8080` 접속 후 다음을 확인한다:
1. 최초 비밀번호(`250707`)를 설정하고 `/sources`로 자동 이동하는지 확인.
2. `/sources`에서 여행 기간을 설정하고, 구글맵 공유 링크로 소스를 2~3개 등록(비고도 하나 입력)한 뒤(또는 파싱 실패 시 수동으로 이름/좌표 입력) 목록에 비고까지 나타나는지 확인.
3. 풋터 탭으로 `/plan`으로 이동 — 지도에 등록한 소스들의 마커가 표시되는지 확인.
4. 가용 목록의 항목을 클릭하면 지도가 해당 위치로 이동하고 마커가 바운스되는지 확인.
5. 지도를 이동/축소해서 뷰포트 밖 소스가 가용 목록에서 사라지는지 확인.
6. 소스를 타임테이블로 드래그해서 추가한 뒤 페이지를 새로고침해도 유지되는지 확인.
7. 타임테이블 항목의 "X" 버튼으로 제거하면 다시 가용 목록에 나타나는지 확인.
8. 날짜 탭을 전환하면 해당 날짜의 타임테이블만 보이는지 확인.
9. 날짜별 참고사항 textarea에 메모를 입력하고 저장한 뒤, 다른 날짜 탭으로 갔다가 다시 돌아와도 메모가 유지되는지, 페이지를 새로고침해도 유지되는지 확인.
10. 참고사항을 지우고 저장하면 빈 상태로 남는지(재방문 시에도 빈 상태) 확인.

- [ ] **Step 3: 커밋**

```bash
git add src/main/resources/static/js/plan.js
git commit -m "feat: add map, viewport filtering, drag-and-drop, and day note editing"
```

---

## Self-Review 완료 사항

- **스펙 커버리지**: 인증(Task 2-3), Trip(Task 4), 구글맵 링크 파싱(Task 5), 소스 CRUD 및 비고(Task 6-7), 풋터 탭(Task 7), 스케줄 배정/해제(Task 8), 날짜별 참고사항 도메인(Task 9), 페이지2 렌더링(Task 10), 지도/뷰포트/드래그앤드롭/참고사항 편집(Task 11) 모두 매핑됨.
- **플레이스홀더 스캔**: TBD/TODO 없음, 모든 스텝에 실제 코드 포함.
- **타입 일관성**: `Source`의 필드명(`scheduledDate`, `sortOrder`, `placeType`, `memo` 등)이 서비스·컨트롤러·JS 전반에서 동일하게 사용됨을 확인. `DayNote`/`DAY_NOTES` 키가 `LocalDate`의 ISO 문자열(`YYYY-MM-DD`)로 서버·클라이언트 양쪽에서 일관됨. `SESSION_AUTHENTICATED_KEY`는 Task 3에서 한 번만 정의되고 이후 모든 통합 테스트에서 동일하게 참조됨.
- **인터셉터 테스트 순서 문제 해결**: Task 3에서는 아직 `/sources`, `/plan` 컨트롤러가 없으므로 `AuthInterceptor`를 순수 단위 테스트로 검증하고, 실제 엔드포인트를 통한 리다이렉트 확인은 각 컨트롤러가 생긴 Task 4, 7, 8, 9, 10에서 수행하도록 배치함.
- **비고/참고사항 추가 반영**: `Source.memo`(Task 6-7)와 `DayNote`(신규 Task 9, PlanController/plan.js에 Task 10-11에서 연동)로 두 요구사항을 모두 커버함. 둘 다 완전한 자유 입력이며 별도 검증 규칙이 없음을 Global Constraints에 명시.
