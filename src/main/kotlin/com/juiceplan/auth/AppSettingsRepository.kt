package com.juiceplan.auth

import org.springframework.data.jpa.repository.JpaRepository

interface AppSettingsRepository : JpaRepository<AppSettings, Long>
