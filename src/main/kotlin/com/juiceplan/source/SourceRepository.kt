package com.juiceplan.source

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface SourceRepository : JpaRepository<Source, Long> {
    fun findByScheduledDate(scheduledDate: LocalDate): List<Source>
}
