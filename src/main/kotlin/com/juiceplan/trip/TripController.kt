package com.juiceplan.trip

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

data class TripRequest(val startDate: LocalDate, val endDate: LocalDate)

@RestController
class TripController(private val tripService: TripService) {

    /** 여행은 하나뿐이라 upsert다. 두 번 호출해도 새로 만들지 않고 갱신한다. */
    @PostMapping("/api/trip")
    fun save(@RequestBody request: TripRequest): Trip =
        tripService.save(request.startDate, request.endDate)
}
