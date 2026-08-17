package com.juiceplan.budget

import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

data class BudgetItemRequest(
    val name: String,
    val category: BudgetCategory,
    val paymentMethod: PaymentMethod,
    val currency: Currency,
    val amount: Int,
    val settlement: SettlementStatus,
    val memo: String? = null
) {
    fun toInput() = BudgetItemInput(name, category, paymentMethod, currency, amount, settlement, memo)
}

data class RateRequest(val ratePer100Jpy: Int)

/**
 * 저장·수정이 엔티티를 그대로 돌려주는 이유는 SourceController 와 같다 — 클라이언트가
 * 생성된 id 를 알아야 목록에 넣고 이후 수정·삭제를 걸 수 있다.
 *
 * 합계는 서버만 낸다. 항목을 고친 뒤 화면은 /api/budget/summary 를 다시 받아온다.
 * 왕복이 한 번 늘지만 계산 규칙이 JS 에 복제되지 않는다.
 */
@RestController
class BudgetApiController(private val budgetService: BudgetService) {

    @GetMapping("/api/budget/summary")
    fun summary(): BudgetSummary = budgetService.summary()

    @PostMapping("/api/budget/items")
    fun create(@RequestBody request: BudgetItemRequest): BudgetItem =
        budgetService.create(request.toInput())

    @PutMapping("/api/budget/items/{id}")
    fun update(@PathVariable id: Long, @RequestBody request: BudgetItemRequest): BudgetItem =
        budgetService.update(id, request.toInput())

    @DeleteMapping("/api/budget/items/{id}")
    fun delete(@PathVariable id: Long) {
        budgetService.delete(id)
    }

    @PutMapping("/api/budget/rate")
    fun saveRate(@RequestBody request: RateRequest): BudgetSummary {
        budgetService.saveRate(request.ratePer100Jpy)
        return budgetService.summary()
    }
}
