package com.juiceplan.schedule

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 마이그레이션으로 채워지는 첫 일정의 시작 시각 (10:00). */
private const val MIGRATION_FIRST_START_MINUTES = 600

/** 마이그레이션이 일정 사이에 넣는 이동 시간(분). */
private const val MIGRATION_GAP_MINUTES = 30

/**
 * ddl-auto: update가 하지 못하는 스키마 정리를 담당한다. Hibernate는 컬럼과 테이블을 추가만 하고
 * 삭제하지 않으므로, 코드에서 없앤 것들이 DB에 남아 문제를 일으킨다.
 *
 * ApplicationRunner로 등록해 Hibernate가 스키마를 만든 뒤에 실행되도록 한다.
 * 각 단계는 대상이 이미 없으면 아무것도 하지 않으므로 몇 번 실행해도 안전하다.
 */
@Component
class SchemaMigration(private val jdbcTemplate: JdbcTemplate) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        migrate()
    }

    @Transactional
    fun migrate() {
        migrateSortOrderToStartMinutes()
        dropAppSettings()
    }

    /** 인증을 없앴으므로 비밀번호 해시를 담던 테이블을 지운다. */
    private fun dropAppSettings() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS APP_SETTINGS")
    }

    /**
     * sortOrder(순서) 기반 배정을 startMinutes(시각) 기반으로 옮긴다.
     *
     * 엔티티에서 sortOrder를 뺀 뒤에도 DB에는 SORT_ORDER NOT NULL 컬럼이 남아
     * 새 소스 저장이 전부 실패한다. 값을 옮기고 컬럼을 지운다.
     */
    private fun migrateSortOrderToStartMinutes() {
        if (!sortOrderColumnExists()) return

        val rows = jdbcTemplate.queryForList(
            """
            SELECT ID, SCHEDULED_DATE, DURATION_MINUTES
            FROM SOURCE
            WHERE SCHEDULED_DATE IS NOT NULL
            ORDER BY SCHEDULED_DATE, SORT_ORDER
            """.trimIndent()
        )

        var currentDate: Any? = null
        var nextStart = MIGRATION_FIRST_START_MINUTES

        for (row in rows) {
            val date = row["SCHEDULED_DATE"]
            if (date != currentDate) {
                currentDate = date
                nextStart = MIGRATION_FIRST_START_MINUTES
            }

            val start = minOf(nextStart, LAST_START_MINUTES)
            jdbcTemplate.update("UPDATE SOURCE SET START_MINUTES = ? WHERE ID = ?", start, row["ID"])

            val duration = (row["DURATION_MINUTES"] as Number).toInt()
            nextStart = roundUpToSlot(start + duration + MIGRATION_GAP_MINUTES)
        }

        jdbcTemplate.execute("ALTER TABLE SOURCE DROP COLUMN IF EXISTS SORT_ORDER")
    }

    /** durationMinutes는 30의 배수가 아닐 수 있으므로, 배정 시각이 30분 슬롯에 맞도록 위로 올린다. */
    private fun roundUpToSlot(minutes: Int): Int =
        ((minutes + SLOT_MINUTES - 1) / SLOT_MINUTES) * SLOT_MINUTES

    private fun sortOrderColumnExists(): Boolean =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_NAME = 'SOURCE' AND COLUMN_NAME = 'SORT_ORDER'
            """.trimIndent(),
            Int::class.java
        )!! > 0
}
