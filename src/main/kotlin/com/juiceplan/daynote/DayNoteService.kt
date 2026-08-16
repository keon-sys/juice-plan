package com.juiceplan.daynote

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class DayNoteService(private val dayNoteRepository: DayNoteRepository) {

    fun allForRange(startDate: LocalDate, endDate: LocalDate): Map<LocalDate, String> =
        dayNoteRepository.findByDateBetween(startDate, endDate).associate { it.date to it.memo }

    @Transactional
    fun save(date: LocalDate, memo: String) {
        val existing = dayNoteRepository.findByDate(date)
        if (memo.isBlank()) {
            existing?.let { dayNoteRepository.delete(it) }
            return
        }
        if (existing != null) {
            existing.memo = memo
        } else {
            dayNoteRepository.save(DayNote(date = date, memo = memo))
        }
    }
}
