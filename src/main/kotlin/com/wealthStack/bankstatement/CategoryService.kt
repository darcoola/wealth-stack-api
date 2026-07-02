package com.wealthStack.bankstatement

import org.springframework.transaction.annotation.Transactional
import com.wealthStack.bankstatement.search.AutoCategorizationService

/**
 * Command side of the category dictionary: create, rename, delete entries, and assign/unassign a
 * category to a single operation. Names are unique (trimmed, case-sensitive). Deleting a category
 * that is in use first un-assigns it from every operation (back to Uncategorized) so the foreign
 * key never blocks the delete.
 */
open class CategoryService(
    private val categoryRepository: CategoryRepository,
    private val bankingOperationRepository: BankingOperationRepository,
    private val autoCategorizationService: AutoCategorizationService
) {

    @Transactional
    open fun create(name: String, type: CategoryType = CategoryType.SPENDING): Category {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Category name must not be blank" }
        require(categoryRepository.findByName(trimmed) == null) { "Category '$trimmed' already exists" }
        return categoryRepository.save(Category(trimmed, type))
    }

    @Transactional
    open fun rename(id: Long, name: String): Category = update(id, name, null)

    /** Updates the name and, when [type] is given, the spending/income type of a category. */
    @Transactional
    open fun update(id: Long, name: String, type: CategoryType?): Category {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Category name must not be blank" }
        val category = categoryRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Category $id not found") }
        val clash = categoryRepository.findByName(trimmed)
        require(clash == null || clash.id == id) { "Category '$trimmed' already exists" }
        category.name = trimmed
        type?.let { category.type = it }
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
        operation.needsVerification = false
        val saved = bankingOperationRepository.save(operation)
        if (saved.category != null) {
            autoCategorizationService.indexOperations(listOf(saved))
        }
        return saved
    }

    /** Assigns [categoryId] to every given operation, or clears it (Uncategorized) when null. */
    @Transactional
    open fun assignToOperations(operationIds: List<Long>, categoryId: Long?): List<BankingOperation> {
        val category = categoryId?.let {
            categoryRepository.findById(it)
                .orElseThrow { IllegalArgumentException("Category $it not found") }
        }
        val operations = bankingOperationRepository.findAllById(operationIds)
        operations.forEach { 
            it.category = category 
            it.needsVerification = false
        }
        val saved = bankingOperationRepository.saveAll(operations).toList()
        if (category != null) {
            autoCategorizationService.indexOperations(saved)
        }
        return saved
    }

    @Transactional
    open fun acceptPrediction(operationId: Long): BankingOperation {
        val operation = bankingOperationRepository.findById(operationId)
            .orElseThrow { IllegalArgumentException("Operation $operationId not found") }
        operation.needsVerification = false
        return bankingOperationRepository.save(operation)
    }

    @Transactional
    open fun acceptPredictions(operationIds: List<Long>): List<BankingOperation> {
        val operations = bankingOperationRepository.findAllById(operationIds)
        operations.forEach { it.needsVerification = false }
        return bankingOperationRepository.saveAll(operations).toList()
    }

    /** Indexes all already-categorized operations into Elasticsearch to train the auto-categorization. */
    @Transactional(readOnly = true)
    open fun syncCategorizedOperationsToSearch(): Int {
        val categorized = bankingOperationRepository.findAll().filter { it.category != null }
        if (categorized.isNotEmpty()) {
            autoCategorizationService.indexOperations(categorized)
        }
        return categorized.size
    }
}
