package com.juiceplan.schedule

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class SchemaMigrationTest {

    @Autowired lateinit var jdbcTemplate: JdbcTemplate
    @Autowired lateinit var migration: SchemaMigration

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("DELETE FROM SOURCE")
        // 앱 기동 시 migration이 이미 한 번 돌아 컬럼을 지웠을 수 있으므로 되살린다.
        jdbcTemplate.execute("ALTER TABLE SOURCE ADD COLUMN IF NOT EXISTS SORT_ORDER INT NOT NULL DEFAULT 0")
    }

    private fun insert(id: Long, name: String, date: String?, sortOrder: Int, durationMinutes: Int) {
        jdbcTemplate.update(
            """
            INSERT INTO SOURCE
              (ID, GOOGLE_MAPS_URL, NAME, LATITUDE, LONGITUDE, PLACE_TYPE,
               DURATION_MINUTES, RESERVATION_REQUIRED, SCHEDULED_DATE, START_MINUTES, SORT_ORDER)
            VALUES (?, 'https://maps.app.goo.gl/x', ?, 37.0, 127.0, 'ATTRACTION', ?, FALSE, ?, NULL, ?)
            """.trimIndent(),
            id, name, durationMinutes, date, sortOrder
        )
    }

    private fun startMinutesOf(id: Long): Int? =
        jdbcTemplate.queryForObject("SELECT START_MINUTES FROM SOURCE WHERE ID = ?", Int::class.javaObjectType, id)

    private fun sortOrderColumnExists(): Boolean =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_NAME = 'SOURCE' AND COLUMN_NAME = 'SORT_ORDER'
            """.trimIndent(),
            Int::class.java
        )!! > 0

    @Test
    fun `fills start times sequentially from 1000 within each date`() {
        insert(1, "A", "2026-09-01", 0, 60)
        insert(2, "B", "2026-09-01", 1, 90)
        insert(3, "C", "2026-09-01", 2, 30)

        migration.migrate()

        // A: 10:00 = 600
        assertEquals(600, startMinutesOf(1))
        // B: 600 + 60 + 30 = 690 (11:30)
        assertEquals(690, startMinutesOf(2))
        // C: 690 + 90 + 30 = 810 (13:30)
        assertEquals(810, startMinutesOf(3))
    }

    @Test
    fun `each date restarts from 1000`() {
        insert(1, "A", "2026-09-01", 0, 60)
        insert(2, "B", "2026-09-02", 0, 60)

        migration.migrate()

        assertEquals(600, startMinutesOf(1))
        assertEquals(600, startMinutesOf(2))
    }

    @Test
    fun `rounds up to the next 30 minute slot when duration is not a multiple of 30`() {
        insert(1, "A", "2026-09-01", 0, 45)
        insert(2, "B", "2026-09-01", 1, 60)

        migration.migrate()

        assertEquals(600, startMinutesOf(1))
        // 600 + 45 + 30 = 675 -> 30분 위로 올림 -> 690
        assertEquals(690, startMinutesOf(2))
    }

    @Test
    fun `clamps to the last placeable slot when the day overflows`() {
        insert(1, "A", "2026-09-01", 0, 600)
        insert(2, "B", "2026-09-01", 1, 600)
        insert(3, "C", "2026-09-01", 2, 600)

        migration.migrate()

        assertEquals(600, startMinutesOf(1))
        // 600 + 600 + 30 = 1230 (20:30)
        assertEquals(1230, startMinutesOf(2))
        // 1230 + 600 + 30 = 1860 -> 1650(27:30)으로 고정
        assertEquals(1650, startMinutesOf(3))
    }

    @Test
    fun `leaves unscheduled sources with a null start time`() {
        insert(1, "A", null, 0, 60)

        migration.migrate()

        assertEquals(null, startMinutesOf(1))
    }

    @Test
    fun `drops the sort_order column`() {
        insert(1, "A", "2026-09-01", 0, 60)

        migration.migrate()

        assertEquals(false, sortOrderColumnExists())
    }

    @Test
    fun `running twice is safe`() {
        insert(1, "A", "2026-09-01", 0, 60)

        migration.migrate()
        migration.migrate()

        assertEquals(600, startMinutesOf(1))
        assertEquals(false, sortOrderColumnExists())
    }

    @Test
    fun `drops the app_settings table left over from the removed auth feature`() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS APP_SETTINGS (ID BIGINT PRIMARY KEY, PASSWORD_HASH VARCHAR(255))")

        migration.migrate()

        assertEquals(false, tableExists("APP_SETTINGS"))
    }

    @Test
    fun `dropping app_settings is safe when the table is already gone`() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS APP_SETTINGS")

        migration.migrate()

        assertEquals(false, tableExists("APP_SETTINGS"))
    }

    private fun tableExists(name: String): Boolean =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?",
            Int::class.java,
            name
        )!! > 0
}
