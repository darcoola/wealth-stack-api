package com.wealthStack.bankstatement

import org.springframework.transaction.annotation.Transactional

/**
 * Command side of the category dictionary: create, rename, delete entries, and assign/unassign a
 * category to a single operation. Names are unique (trimmed, case-sensitive). Deleting a category
 * that is in use first un-assigns it from every operation (back to Uncategorized) so the foreign
 * key never blocks the delete.
 */
open class CategoryService(
    private val categoryRepository: CategoryRepository,
    private val bankingOperationRepository: BankingOperationRepository
) {

    @Transactional
    open fun create(name: String): Category {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Category name must not be blank" }
        require(categoryRepository.findByName(trimmed) == null) { "Category '$trimmed' already exists" }
        return categoryRepository.save(Category(trimmed))
    }

    @Transactional
    open fun rename(id: Long, name: String): Category {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Category name must not be blank" }
        val category = categoryRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Category $id not found") }
        val clash = categoryRepository.findByName(trimmed)
        require(clash == null || clash.id == id) { "Category '$trimmed' already exists" }
        category.name = trimmed
        return categoryRepository.save(category)
    }

    @Transactional
    open fun delete(id: Long) {
        val category = categoryRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Category $id not found") }
        val affected = bankingOperationRepository.findAllByCategory(category)
        affected.forEach { it.category = null }
        bankingOperationRepository.saveAll(affected)
        categoryRepository.delete(category)
    }

    /** Assigns [categoryId] to the operation, or clears it (back to Uncategorized) when null. */
    @Transactional
    open fun assignToOperation(operationId: Long, categoryId: Long?): BankingOperation {
        val operation = bankingOperationRepository.findById(operationId)
            .orElseThrow { IllegalArgumentException("Operation $operationId not found") }
        operation.category = categoryId?.let {
            categoryRepository.findById(it)
                .orElseThrow { IllegalArgumentException("Category $it not found") }
        }
        return bankingOperationRepository.save(operation)
    }
}
