package com.juiceplan.check

import org.springframework.data.jpa.repository.JpaRepository

interface CheckItemRepository : JpaRepository<CheckItem, Long>
