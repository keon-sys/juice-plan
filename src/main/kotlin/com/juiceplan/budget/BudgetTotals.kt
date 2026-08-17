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
