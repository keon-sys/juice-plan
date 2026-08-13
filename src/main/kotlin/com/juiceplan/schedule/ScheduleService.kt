package com.juiceplan.schedule

import com.juiceplan.source.SourceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class ScheduleService(private val sourceRepository: SourceRepository) {

    @Transactional
    fun assignDay(date: LocalDate, orderedSourceIds: List<Long>) {
        val newIdSet = orderedSourceIds.toSet()

        sourceRepository.findByScheduledDate(date)
            .filter { it.id !in newIdSet }
            .forEach {
                it.scheduledDate = null
                it.sortOrder = 0
            }

        orderedSourceIds.forEachIndexed { index, id ->
            val source = sourceRepository.findById(id)
                .orElseThrow { NoSuchElementException("소스를 찾을 수 없습니다: $id") }
            source.scheduledDate = date
            source.sortOrder = index
        }
    }

    @Transactional
    fun remove(sourceId: Long) {
        val source = sourceRepository.findById(sourceId)
            .orElseThrow { NoSuchElementException("소스를 찾을 수 없습니다: $sourceId") }
        source.scheduledDate = null
        source.sortOrder = 0
    }
}
