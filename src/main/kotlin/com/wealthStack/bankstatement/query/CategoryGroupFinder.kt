package com.wealthStack.bankstatement.query

import com.wealthStack.bankstatement.CategoryGroupRepository

open class CategoryGroupFinder(
    private val repository: CategoryGroupRepository
) {

    open fun findAll(): List<CategoryGroupDto> = repository.findAll()
        .sortedBy { it.name.lowercase() }
        .map { CategoryGroupDto(id = it.id!!, name = it.name) }
}
