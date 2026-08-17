package com.juiceplan.budget

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 화면이 보내는 항목 값. 엔티티와 달리 id 가 없고 이름에 앞뒤 공백이 남아 있다. */
data class BudgetItemInput(
    val name: String,
    val category: BudgetCategory,
    val paymentMethod: PaymentMethod,
    val currency: Currency,
    val amount: Int,
    val settlement: SettlementStatus,
    val memo: String? = null
)

@Service
class BudgetService(
    private val itemRepository: BudgetItemRepository,
    private val settingRepository: BudgetSettingRepository
) {
    /**
     * 카테고리 선언 순 → id 순. 화면이 카테고리별로 묶어 그리므로 이 순서가 곧 화면 순서다.
     * 정렬 컬럼을 따로 두지 않는 이유는 사람이 순서를 손으로 바꿀 일이 없어서다.
     */
    fun list(): List<BudgetItem> =
        itemRepository.findAll().sortedWith(compareBy({ it.category.ordinal }, { it.id }))

    fun summary(): BudgetSummary = BudgetTotals.summarize(list(), rate())

    fun rate(): Int = setting().ratePer100Jpy

    @Transactional
    fun saveRate(ratePer100Jpy: Int) {
        require(ratePer100Jpy > 0) { "환율은 0보다 커야 합니다." }
        settingRepository.save(setting().also { it.ratePer100Jpy = ratePer100Jpy })
    }

    @Transactional
    fun create(input: BudgetItemInput): BudgetItem {
        val clean = input.validated()
        return itemRepository.save(
            BudgetItem(
                name = clean.name,
                category = clean.category,
                paymentMethod = clean.paymentMethod,
                currency = clean.currency,
                amount = clean.amount,
                settlement = clean.settlement,
                memo = clean.memo
            )
        )
    }

    @Transactional
    fun update(id: Long, input: BudgetItemInput): BudgetItem {
        val clean = input.validated()
        val item = find(id)
        item.name = clean.name
        item.category = clean.category
        item.paymentMethod = clean.paymentMethod
        item.currency = clean.currency
        item.amount = clean.amount
        item.settlement = clean.settlement
        item.memo = clean.memo
        return itemRepository.save(item)
    }

    @Transactional
    fun delete(id: Long) {
        itemRepository.delete(find(id))
    }

    private fun find(id: Long): BudgetItem =
        itemRepository.findById(id).orElseThrow { NoSuchElementException("없는 지출 항목입니다.") }

    /** 설정 행은 처음 읽을 때 기본값으로 만들어진다. 시드가 돌기 전에 열어도 화면이 비지 않는다. */
    private fun setting(): BudgetSetting =
        settingRepository.findById(SETTING_ID).orElseGet { settingRepository.save(BudgetSetting()) }
}

/** 이름은 앞뒤 공백만 잘라내고 비어 있어도 통과시킨다. 금액은 음수만 막는다. */
private fun BudgetItemInput.validated(): BudgetItemInput {
    require(amount >= 0) { "금액은 0보다 작을 수 없습니다." }
    return copy(name = name.trim(), memo = memo?.trim()?.ifEmpty { null })
}
