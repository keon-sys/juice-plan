package com.juiceplan.daynote

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface DayNoteRepository : JpaRepository<DayNote, Long> {
    fun findByDate(date: LocalDate): DayNote?
    fun findByDateBetween(start: LocalDate, end: LocalDate): List<DayNote>
}
