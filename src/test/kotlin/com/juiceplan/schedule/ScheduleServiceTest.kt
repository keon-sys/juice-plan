package com.juiceplan.schedule

import com.juiceplan.source.PlaceType
import com.juiceplan.source.Source
import com.juiceplan.source.SourceRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import java.time.LocalDate

@DataJpaTest
@Import(ScheduleService::class)
class ScheduleServiceTest {

    @Autowired lateinit var sourceRepository: SourceRepository
    @Autowired lateinit var scheduleService: ScheduleService

    private val date = LocalDate.of(2026, 9, 1)

    private fun newSource(name: String) = sourceRepository.save(
        Source(
            googleMapsUrl = "https://maps.app.goo.gl/x",
            name = name,
            latitude = 37.0,
            longitude = 127.0,
            placeType = PlaceType.ATTRACTION,
            durationMinutes = 60,
            reservationRequired = false
        )
    )

    @Test
    fun `assign sets scheduledDate and startMinutes together`() {
        val a = newSource("A")

        scheduleService.assign(a.id, date, 600)

        val reloaded = sourceRepository.findById(a.id).get()
        assertEquals(date, reloaded.scheduledDate)
        assertEquals(600, reloaded.startMinutes)
    }

    @Test
    fun `assign moves an already scheduled source to another date and time`() {
        val a = newSource("A")
        scheduleService.assign(a.id, date, 600)

        scheduleService.assign(a.id, date.plusDays(1), 900)

        val reloaded = sourceRepository.findById(a.id).get()
        assertEquals(date.plusDays(1), reloaded.scheduledDate)
        assertEquals(900, reloaded.startMinutes)
    }

    @Test
    fun `remove clears scheduledDate and startMinutes together`() {
        val a = newSource("A")
        scheduleService.assign(a.id, date, 600)

        scheduleService.remove(a.id)

        val reloaded = sourceRepository.findById(a.id).get()
        assertNull(reloaded.scheduledDate)
        assertNull(reloaded.startMinutes)
    }

    @Test
    fun `assign accepts the boundary slots 0400 and 2730`() {
        val a = newSource("A")
        val b = newSource("B")

        scheduleService.assign(a.id, date, 240)
        scheduleService.assign(b.id, date, 1650)

        assertEquals(240, sourceRepository.findById(a.id).get().startMinutes)
        assertEquals(1650, sourceRepository.findById(b.id).get().startMinutes)
    }

    @Test
    fun `assign rejects a time before 0400`() {
        val a = newSource("A")

        val ex = assertThrows(IllegalArgumentException::class.java) {
            scheduleService.assign(a.id, date, 210)
        }
        assertEquals("시간은 04:00~27:30 사이여야 합니다.", ex.message)
    }

    @Test
    fun `assign rejects 2800 because it is the grid edge, not a placeable slot`() {
        val a = newSource("A")

        assertThrows(IllegalArgumentException::class.java) {
            scheduleService.assign(a.id, date, 1680)
        }
    }

    @Test
    fun `assign rejects a time that is not on a 30 minute slot`() {
        val a = newSource("A")

        val ex = assertThrows(IllegalArgumentException::class.java) {
            scheduleService.assign(a.id, date, 615)
        }
        assertEquals("시간은 30분 단위여야 합니다.", ex.message)
    }

    @Test
    fun `assign rejects an unknown source id`() {
        assertThrows(NoSuchElementException::class.java) {
            scheduleService.assign(9999L, date, 600)
        }
    }

    @Test
    fun `assign allows two sources to overlap in time`() {
        val a = newSource("A")
        val b = newSource("B")

        scheduleService.assign(a.id, date, 600)
        scheduleService.assign(b.id, date, 600)

        assertEquals(600, sourceRepository.findById(a.id).get().startMinutes)
        assertEquals(600, sourceRepository.findById(b.id).get().startMinutes)
    }
}
