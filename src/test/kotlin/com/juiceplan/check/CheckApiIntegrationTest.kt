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
