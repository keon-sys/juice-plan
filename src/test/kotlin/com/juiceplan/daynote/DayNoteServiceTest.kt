package com.juiceplan.daynote

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import java.time.LocalDate

@DataJpaTest
@Import(DayNoteService::class)
class DayNoteServiceTest {

    @Autowired lateinit var dayNoteRepository: DayNoteRepository
    @Autowired lateinit var dayNoteService: DayNoteService

    @Test
    fun `save creates a new note when none exists for the date`() {
        val date = LocalDate.of(2026, 9, 1)

        dayNoteService.save(date, "오전엔 우천 예보")

        assertEquals("오전엔 우천 예보", dayNoteRepository.findByDate(date)?.memo)
    }

    @Test
    fun `save updates the existing note instead of creating a duplicate`() {
        val date = LocalDate.of(2026, 9, 1)
        dayNoteService.save(date, "첫 메모")

        dayNoteService.save(date, "수정된 메모")

        val notes = dayNoteRepository.findByDateBetween(date, date)
        assertEquals(1, notes.size)
        assertEquals("수정된 메모", notes[0].memo)
    }

    @Test
    fun `save with blank memo deletes the existing note`() {
        val date = LocalDate.of(2026, 9, 1)
        dayNoteService.save(date, "지울 메모")

        dayNoteService.save(date, "")

        assertNull(dayNoteRepository.findByDate(date))
    }

    @Test
    fun `allForRange returns a map keyed by date for dates within range`() {
        dayNoteService.save(LocalDate.of(2026, 9, 1), "1일차 메모")
        dayNoteService.save(LocalDate.of(2026, 9, 3), "3일차 메모")
        dayNoteService.save(LocalDate.of(2026, 9, 10), "범위 밖 메모")

        val result = dayNoteService.allForRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5))

        assertEquals(2, result.size)
        assertEquals("1일차 메모", result[LocalDate.of(2026, 9, 1)])
        assertEquals("3일차 메모", result[LocalDate.of(2026, 9, 3)])
    }
}
