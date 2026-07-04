package com.wealthStack.bankstatement.query

import com.wealthStack.bankstatement.CategoryGroupRepository

open class CategoryGroupFinder(
    private val repository: CategoryGroupRepository
) {

    open fun findAll(partyId: Long): List<CategoryGroupDto> = repository.findAllByPartyId(partyId)
        .sortedBy { it.name.lowercase() }
        .map { CategoryGroupDto(id = it.id!!, name = it.name) }
}
