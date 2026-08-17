package com.juiceplan.nav

import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
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
