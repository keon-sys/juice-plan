package com.juiceplan.check

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/** 어느 탭의 항목인가. 값을 늘리는 것이 곧 탭을 하나 더하는 일이다. */
enum class CheckList { SHOPPING, PACKING, TODO }

/**
 * 체크리스트 항목 하나. 세 목록이 전부 같은 모양(이름·체크·메모)이라 엔티티도 하나다.
 * 목록마다 엔티티를 나누면 같은 코드를 세 번 쓰게 되고, 목록을 더할 때마다 또 한 벌이 는다.
 *
 * enum 은 문자열로 저장한다. Hibernate 는 H2 에서 enum 필드를 ENUM('A','B') 네이티브 컬럼으로
 * 만드는데 ddl-auto: update 가 그 값 목록을 넓혀주지 않아, 목록을 하나 더하면 기존 DB 가
 * 새 값을 거부한다. Source.placeType, BudgetItem.category 와 같은 이유다.
 */
@Entity
class CheckItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    var list: CheckList,

    var name: String,

    var checked: Boolean = false,

    var memo: String? = null
)
