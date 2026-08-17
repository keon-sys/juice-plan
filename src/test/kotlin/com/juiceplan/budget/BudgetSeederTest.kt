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

    // 시드는 "비어 있을 때만" 도는 게 핵심이다. 무엇이 시드를 막는지 시험하려면
    // 빈 상태에서 출발한다는 것부터 확실해야 한다.
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
