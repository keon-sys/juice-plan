package com.juiceplan.schedule

import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

data class AssignRequest(val date: LocalDate, val startMinutes: Int)

@RestController
class ScheduleController(private val scheduleService: ScheduleService) {

    @PutMapping("/api/schedule/{sourceId}")
    fun assign(@PathVariable sourceId: Long, @RequestBody request: AssignRequest) {
        scheduleService.assign(sourceId, request.date, request.startMinutes)
    }

    @DeleteMapping("/api/schedule/{sourceId}")
    fun remove(@PathVariable sourceId: Long) {
        scheduleService.remove(sourceId)
    }
}
