package com.juiceplan.budget

import org.springframework.data.jpa.repository.JpaRepository

interface BudgetItemRepository : JpaRepository<BudgetItem, Long>

interface BudgetSettingRepository : JpaRepository<BudgetSetting, Long>
