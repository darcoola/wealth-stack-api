package com.wealthStack.bankstatement

import org.springframework.data.jpa.repository.JpaRepository

interface CategoryRepository : JpaRepository<Category, Long> {
    fun findByPartyIdAndName(partyId: Long, name: String): Category?

    fun findAllByPartyId(partyId: Long): List<Category>

    fun findAllByGroup(group: CategoryGroup): List<Category>
}
