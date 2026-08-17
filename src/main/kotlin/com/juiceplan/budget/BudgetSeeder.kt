package com.juiceplan.budget

import com.juiceplan.budget.BudgetCategory.ACTIVITY
import com.juiceplan.budget.BudgetCategory.ETC
import com.juiceplan.budget.BudgetCategory.FLIGHT
import com.juiceplan.budget.BudgetCategory.FOOD
import com.juiceplan.budget.BudgetCategory.HOTEL
import com.juiceplan.budget.BudgetCategory.SHOPPING
import com.juiceplan.budget.BudgetCategory.TRANSIT
import com.juiceplan.budget.Currency.JPY
import com.juiceplan.budget.Currency.KRW
import com.juiceplan.budget.PaymentMethod.CASH
import com.juiceplan.budget.PaymentMethod.CREDIT_CARD
import com.juiceplan.budget.PaymentMethod.TRAVEL_LOG
import com.juiceplan.budget.SettlementStatus.DONE
import com.juiceplan.budget.SettlementStatus.NOT_APPLICABLE
import com.juiceplan.budget.SettlementStatus.PENDING
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 예산 화면을 처음 켤 때 이미 쓰던 표를 그대로 채워 넣는다.
 *
 * 항목이 하나라도 있으면 아무것도 하지 않는다. SchemaMigration 과 같은 원칙이라 몇 번을
 * 띄워도 안전하고, 사람이 지운 항목이 다음 부팅에 되살아나지 않는다.
 *
 * 금액이 0인 항목의 통화는 앞으로 실제로 결제할 통화로 넣었다. 일본에서 쓸 식비·쇼핑은
 * 엔화, 한국에서 결제하는 숙박·eSIM·보험은 원화다. 금액이 0이라 합계는 달라지지 않고
 * 화면에서 바로 고칠 수 있다.
 */
@Component
class BudgetSeeder(
    private val itemRepository: BudgetItemRepository,
    private val settingRepository: BudgetSettingRepository
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        seed()
    }

    @Transactional
    fun seed() {
        if (settingRepository.findById(SETTING_ID).isEmpty) {
            settingRepository.save(BudgetSetting(ratePer100Jpy = DEFAULT_RATE_PER_100_JPY))
        }
        if (itemRepository.count() > 0L) return
        itemRepository.saveAll(initialItems())
    }
}

private fun item(
    name: String,
    category: BudgetCategory,
    paymentMethod: PaymentMethod,
    currency: Currency,
    amount: Int,
    settlement: SettlementStatus,
    memo: String? = null
) = BudgetItem(
    name = name,
    category = category,
    paymentMethod = paymentMethod,
    currency = currency,
    amount = amount,
    settlement = settlement,
    memo = memo
)

/** 식비는 "몇 끼를 어떤 수단으로 낼지"만 잡아둔 자리라 이름과 금액이 비어 있다. */
private fun meal(paymentMethod: PaymentMethod) = item("", FOOD, paymentMethod, JPY, 0, PENDING)

private fun initialItems(): List<BudgetItem> = listOf(
    item("왕복 항공권 (2인)", FLIGHT, CREDIT_CARD, KRW, 853_800, NOT_APPLICABLE, "이스타항공 사전 결제 완료(각자 결제)"),
    item("코코 호텔 스스키노 (5박, 2인)", HOTEL, CREDIT_CARD, KRW, 0, PENDING, "조식 미포함, 스스키노역 근처"),
    item("신치토세공항-스스키노 왕복 교통 (2인)", TRANSIT, TRAVEL_LOG, JPY, 5_440, PENDING, "JR 쾌속에어포트 + 지하철"),
    item("오타루 왕복 JR 열차 (2인)", TRANSIT, TRAVEL_LOG, JPY, 3_000, PENDING, "JR 하코다테선 지정석/자유석"),
    item("주말 도니치카 패스 1일권 (2인)", TRANSIT, CASH, JPY, 1_040, PENDING, "2일차 일요일 지하철 무제한"),
    item("기타 시내 지하철/버스 충전 (2인)", TRANSIT, TRAVEL_LOG, JPY, 4_000, PENDING, "IC카드 충전식 사용"),
    meal(TRAVEL_LOG),
    meal(TRAVEL_LOG),
    meal(CASH),
    meal(TRAVEL_LOG),
    meal(TRAVEL_LOG),
    meal(CREDIT_CARD),
    meal(TRAVEL_LOG),
    meal(CASH),
    meal(TRAVEL_LOG),
    item("4일차 오타루 운하 크루즈 (2인)", ACTIVITY, TRAVEL_LOG, JPY, 3_600, PENDING, "운하 크루즈 탑승권"),
    meal(TRAVEL_LOG),
    item("돈키호테 쇼핑 & 기념품", SHOPPING, CREDIT_CARD, JPY, 0, PENDING, "의약품, 화장품, 소품"),
    item("공항 면세점 과자/사케 선물", SHOPPING, CREDIT_CARD, JPY, 0, PENDING, "시로이코이비토, 로이스"),
    item("일본 eSIM 6일권 (2인)", ETC, CREDIT_CARD, KRW, 0, DONE, "매일 2GB eSIM"),
    item("해외 여행자 보험 (2인)", ETC, CREDIT_CARD, KRW, 0, DONE, "기본 플랜"),
)
