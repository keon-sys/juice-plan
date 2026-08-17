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

    // @DataJpaTest 는 테스트마다 롤백하지만, 시작 상태를 눈에 보이게 못박아 둔다.
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
