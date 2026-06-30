package com.wealthStack.bankstatement.query

import com.wealthStack.bankstatement.CategoryRepository

open class CategoryFinder(
    private val repository: CategoryRepository
) {

    open fun findAll(): List<CategoryDto> = repository.findAll()
        .sortedBy { it.name.lowercase() }
        .map { CategoryDto(id = it.id!!, name = it.name) }
}
