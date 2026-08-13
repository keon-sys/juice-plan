package com.juiceplan.schedule

import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

data class AssignDayRequest(val sourceIds: List<Long>)

@RestController
class ScheduleController(private val scheduleService: ScheduleService) {

    @PostMapping("/api/schedule/day/{date}")
    fun assignDay(@PathVariable date: String, @RequestBody request: AssignDayRequest) {
        scheduleService.assignDay(LocalDate.parse(date), request.sourceIds)
    }

    @DeleteMapping("/api/schedule/{sourceId}")
    fun remove(@PathVariable sourceId: Long) {
        scheduleService.remove(sourceId)
    }
}
