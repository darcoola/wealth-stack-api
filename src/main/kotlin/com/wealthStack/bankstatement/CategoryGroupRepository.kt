package com.wealthStack.bankstatement

import org.springframework.data.jpa.repository.JpaRepository

interface CategoryGroupRepository : JpaRepository<CategoryGroup, Long> {
    fun findByPartyIdAndName(partyId: Long, name: String): CategoryGroup?

    fun findAllByPartyId(partyId: Long): List<CategoryGroup>
}
