package com.juiceplan.budget

import jakarta.persistence.Entity
import jakarta.persistence.Id

/** 설정은 한 행뿐이라 id 를 고정한다. */
const val SETTING_ID = 1L

/** 기본 환율. 100엔 = ₩900. */
const val DEFAULT_RATE_PER_100_JPY = 900

/**
 * 예산 화면의 설정. 지금은 환율 하나뿐이다.
 *
 * 단위는 화면·API·DB 전부 "100엔당 원"인 정수다. 사람이 환율을 말할 때 쓰는 단위를
 * 그대로 저장하므로 어디서도 단위를 바꿔 담을 일이 없다.
 *
 * 테이블 이름은 BUDGET_SETTING 이다. 예전 인증이 쓰던 APP_SETTINGS 는
 * SchemaMigration.dropAppSettings() 가 매 부팅마다 지우므로 그 이름을 재사용하면 안 된다.
 */
@Entity
class BudgetSetting(
    @Id
    val id: Long = SETTING_ID,

    var ratePer100Jpy: Int = DEFAULT_RATE_PER_100_JPY
)
