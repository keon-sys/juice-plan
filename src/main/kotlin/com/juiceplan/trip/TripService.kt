package com.juiceplan.trip

import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class TripService(private val tripRepository: TripRepository) {

    fun current(): Trip? = tripRepository.findAll().firstOrNull()

    fun save(startDate: LocalDate, endDate: LocalDate): Trip {
        require(!startDate.isAfter(endDate)) { "시작일은 종료일보다 늦을 수 없습니다." }
        val existing = current()
        val trip = if (existing != null) {
            existing.startDate = startDate
            existing.endDate = endDate
            existing
        } else {
            Trip(startDate = startDate, endDate = endDate)
        }
        return tripRepository.save(trip)
    }
}
