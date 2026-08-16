package com.juiceplan.trip

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class TripServiceTest {

    private val repository = mockk<TripRepository>()
    private val service = TripService(repository)

    @Test
    fun `creates a new trip when none exists`() {
        every { repository.findAll() } returns emptyList()
        every { repository.save(any()) } answers { firstArg() }

        val trip = service.save(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5))

        assertEquals(LocalDate.of(2026, 9, 1), trip.startDate)
        assertEquals(LocalDate.of(2026, 9, 5), trip.endDate)
    }

    @Test
    fun `updates the existing trip instead of creating a second one`() {
        val existing = Trip(id = 1, startDate = LocalDate.of(2026, 1, 1), endDate = LocalDate.of(2026, 1, 2))
        every { repository.findAll() } returns listOf(existing)
        every { repository.save(any()) } answers { firstArg() }

        val trip = service.save(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5))

        assertEquals(1L, trip.id)
        assertEquals(LocalDate.of(2026, 9, 1), trip.startDate)
    }

    @Test
    fun `rejects start date after end date`() {
        every { repository.findAll() } returns emptyList()
        assertThrows<IllegalArgumentException> {
            service.save(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 1))
        }
    }
}
