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
            .andExpect(jsonPath("$.error").value("환율은 0보다 커야 합니다."))
    }
}
