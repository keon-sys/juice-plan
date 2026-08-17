package com.juiceplan.budget

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/** 지출 분류. 요약표의 행 순서가 이 선언 순서다. */
enum class BudgetCategory { FLIGHT, HOTEL, FOOD, TRANSIT, ACTIVITY, SHOPPING, ETC }

enum class PaymentMethod { CREDIT_CARD, TRAVEL_LOG, CASH }

enum class SettlementStatus { PENDING, DONE, NOT_APPLICABLE }

/** 결제한 통화. 항목은 이 통화로만 기입하고 환산값은 저장하지 않는다. */
enum class Currency { JPY, KRW }

/**
 * 지출 항목 하나. 금액은 늘 2인 총액이고 통화는 실제로 결제한 쪽 하나뿐이다.
 *
 * 모든 enum 이 문자열로 저장된다. Hibernate 는 H2 에서 enum 필드를 ENUM('A','B') 네이티브
 * 컬럼으로 만드는데, ddl-auto: update 가 이미 있는 ENUM 의 값 목록을 넓혀주지 않아
 * 종류를 하나 더하면 기존 DB 가 새 값을 거부한다. Source.placeType 과 같은 이유다.
 */
@Entity
class BudgetItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    // 이름 없이 카테고리와 결제 수단만 잡아둔 자리(식비 10건)가 있으므로 빈 문자열을 허용한다
    var name: String,

    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    var category: BudgetCategory,

    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    var paymentMethod: PaymentMethod,

    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    var currency: Currency,

    /** 2인 총액, 결제 통화 기준. 아직 안 정한 항목은 0이다. */
    var amount: Int,

    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    var settlement: SettlementStatus,

    var memo: String? = null
)
