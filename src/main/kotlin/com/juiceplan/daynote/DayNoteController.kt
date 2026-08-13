package com.juiceplan.daynote

import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

data class DayNoteRequest(val memo: String)

@RestController
class DayNoteController(private val dayNoteService: DayNoteService) {

    @PostMapping("/api/day-notes/{date}")
    fun save(@PathVariable date: String, @RequestBody request: DayNoteRequest) {
        dayNoteService.save(LocalDate.parse(date), request.memo)
    }
}
