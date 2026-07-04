package com.wealthStack.bankstatement.query

import com.wealthStack.bankstatement.CategoryRepository

open class CategoryFinder(
    private val repository: CategoryRepository
) {

    open fun findAll(partyId: Long): List<CategoryDto> = repository.findAllByPartyId(partyId)
        .sortedBy { it.name.lowercase() }
        .map { CategoryDto(id = it.id!!, name = it.name, groupId = it.group?.id, groupName = it.group?.name) }
}
