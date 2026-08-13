package com.juiceplan.schedule

import com.juiceplan.source.PlaceType
import com.juiceplan.source.Source
import com.juiceplan.source.SourceRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
    fun `assignDay sets scheduledDate and sequential sortOrder`() {
        val a = newSource("A")
        val b = newSource("B")
        val date = LocalDate.of(2026, 9, 1)

        scheduleService.assignDay(date, listOf(a.id, b.id))

        val reloadedA = sourceRepository.findById(a.id).get()
        val reloadedB = sourceRepository.findById(b.id).get()
        assertEquals(date, reloadedA.scheduledDate)
        assertEquals(0, reloadedA.sortOrder)
        assertEquals(date, reloadedB.scheduledDate)
        assertEquals(1, reloadedB.sortOrder)
    }

    @Test
    fun `assignDay unassigns sources previously on that day but missing from the new list`() {
        val a = newSource("A")
        val b = newSource("B")
        val date = LocalDate.of(2026, 9, 1)
        scheduleService.assignDay(date, listOf(a.id, b.id))

        scheduleService.assignDay(date, listOf(a.id))

        val reloadedB = sourceRepository.findById(b.id).get()
        assertNull(reloadedB.scheduledDate)
    }

    @Test
    fun `remove clears scheduledDate`() {
        val a = newSource("A")
        val date = LocalDate.of(2026, 9, 1)
        scheduleService.assignDay(date, listOf(a.id))

        scheduleService.remove(a.id)

        val reloaded = sourceRepository.findById(a.id).get()
        assertNull(reloaded.scheduledDate)
        assertEquals(0, reloaded.sortOrder)
    }
}
