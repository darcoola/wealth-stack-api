package com.wealthStack.bankstatement

import org.springframework.data.jpa.repository.JpaRepository

interface CategoryGroupRepository : JpaRepository<CategoryGroup, Long> {
    fun findByName(name: String): CategoryGroup?
}
