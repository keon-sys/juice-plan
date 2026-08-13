package com.juiceplan.source

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.util.Optional

class SourceServiceTest {

    private val repository = mockk<SourceRepository>()
    private val service = SourceService(repository)

    private fun validInput(
        reservationRequired: Boolean = false,
        deadline: LocalDate? = null,
        memo: String? = null
    ) = SourceInput(
        googleMapsUrl = "https://maps.app.goo.gl/abc",
        name = "경복궁",
        latitude = 37.5796,
        longitude = 126.9770,
        placeType = PlaceType.ATTRACTION,
        durationHours = 1,
        durationMinutesPart = 30,
        reservationRequired = reservationRequired,
        reservationDeadline = deadline,
        memo = memo
    )

    @Test
    fun `create converts hours and minutes into total minutes`() {
        every { repository.save(any()) } answers { firstArg() }

        val source = service.create(validInput())

        assertEquals(90, source.durationMinutes)
    }

    @Test
    fun `create rejects reservation required without a deadline`() {
        assertThrows<IllegalArgumentException> {
            service.create(validInput(reservationRequired = true, deadline = null))
        }
    }

    @Test
    fun `create accepts reservation required with a deadline`() {
        every { repository.save(any()) } answers { firstArg() }

        val source = service.create(validInput(reservationRequired = true, deadline = LocalDate.of(2026, 8, 1)))

        assertEquals(LocalDate.of(2026, 8, 1), source.reservationDeadline)
    }

    @Test
    fun `create persists an optional memo`() {
        every { repository.save(any()) } answers { firstArg() }

        val source = service.create(validInput(memo = "창가 자리 요청"))

        assertEquals("창가 자리 요청", source.memo)
    }

    @Test
    fun `create leaves memo null when not provided`() {
        every { repository.save(any()) } answers { firstArg() }

        val source = service.create(validInput())

        assertNull(source.memo)
    }

    @Test
    fun `delete removes the source by id`() {
        every { repository.deleteById(5L) } returns Unit

        service.delete(5L)

        verify { repository.deleteById(5L) }
    }

    @Test
    fun `get throws when source does not exist`() {
        every { repository.findById(99L) } returns Optional.empty()

        assertThrows<NoSuchElementException> { service.get(99L) }
    }
}
