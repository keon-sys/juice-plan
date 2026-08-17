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
