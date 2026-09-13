package com.wealthStack.bankstatement

import org.springframework.transaction.annotation.Transactional
import com.wealthStack.bankstatement.search.AutoCategorizationService

/**
 * Command side of the category dictionary: create, rename, delete entries, and assign/unassign a
 * category to a single operation. Names are unique (trimmed, case-sensitive). Deleting a category
 * that is in use first un-assigns it from every operation (back to Uncategorized) so the foreign
 * key never blocks the delete.
 */
/**
 * Whether a category update should change the group. [KeepGroup] leaves it untouched (used by
 * `rename`); [SetGroup] assigns it — including to `null` (Ungrouped) — so "set to no group" is
 * distinguishable from "don't touch the group".
 */
sealed interface GroupChange
data object KeepGroup : GroupChange
data class SetGroup(val groupId: Long?) : GroupChange

open class CategoryService(
    private val categoryRepository: CategoryRepository,
    private val categoryGroupRepository: CategoryGroupRepository,
    private val bankingOperationRepository: BankingOperationRepository,
    private val autoCategorizationService: AutoCategorizationService
) {

    @Transactional
    open fun create(name: String, groupId: Long? = null): Category {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Category name must not be blank" }
        require(categoryRepository.findByName(trimmed) == null) { "Category '$trimmed' already exists" }
        return categoryRepository.save(Category(trimmed, resolveGroup(groupId)))
    }

    @Transactional
    open fun createAll(categories: List<Pair<String, Long?>>): List<Category> {
        return categories.map { (name, groupId) -> create(name, groupId) }
    }

    @Transactional
    open fun rename(id: Long, name: String): Category = update(id, name, KeepGroup)

    /** Updates the name and, when [group] is a [SetGroup], the group a category belongs to. */
    @Transactional
    open fun update(id: Long, name: String, group: GroupChange): Category {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Category name must not be blank" }
        val category = categoryRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Category $id not found") }
        val clash = categoryRepository.findByName(trimmed)
        require(clash == null || clash.id == id) { "Category '$trimmed' already exists" }
        category.name = trimmed
        if (group is SetGroup) category.group = resolveGroup(group.groupId)
        return categoryRepository.save(category)
    }

    private fun resolveGroup(groupId: Long?): CategoryGroup? = groupId?.let {
        categoryGroupRepository.findById(it)
            .orElseThrow { IllegalArgumentException("Group $it not found") }
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
