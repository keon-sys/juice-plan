package com.juiceplan.schedule

import com.juiceplan.source.SourceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/** 타임테이블 하루의 시작(04:00)을 자정 기준 분으로 나타낸 값. */
const val DAY_START_MINUTES = 240

/** 타임테이블 그리드의 아래 경계(28:00 = 다음날 04:00). 배정 가능한 시각이 아니다. */
const val DAY_END_MINUTES = 1680

/** 타임테이블 한 슬롯의 길이(분). 배정 시각은 이 값의 배수여야 한다. */
const val SLOT_MINUTES = 30

/**
 * 배정 가능한 마지막 시작 시각(27:30).
 * 28:00은 그리드의 아래 경계여서 블록을 그릴 높이가 남지 않으므로 시작 시각이 될 수 없다.
 */
const val LAST_START_MINUTES = DAY_END_MINUTES - SLOT_MINUTES

@Service
class ScheduleService(private val sourceRepository: SourceRepository) {

    @Transactional
    fun assign(sourceId: Long, date: LocalDate, startMinutes: Int) {
        require(startMinutes in DAY_START_MINUTES..LAST_START_MINUTES) {
            "시간은 04:00~27:30 사이여야 합니다."
        }
        require(startMinutes % SLOT_MINUTES == 0) {
            "시간은 30분 단위여야 합니다."
        }

        val source = sourceRepository.findById(sourceId)
            .orElseThrow { NoSuchElementException("소스를 찾을 수 없습니다: $sourceId") }
        source.scheduledDate = date
        source.startMinutes = startMinutes
    }

    @Transactional
    fun remove(sourceId: Long) {
        val source = sourceRepository.findById(sourceId)
            .orElseThrow { NoSuchElementException("소스를 찾을 수 없습니다: $sourceId") }
        source.scheduledDate = null
        source.startMinutes = null
    }
}
