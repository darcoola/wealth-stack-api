package com.wealthStack.bankstatement

import org.springframework.transaction.annotation.Transactional
import com.wealthStack.bankstatement.search.AutoCategorizationService

/**
 * Command side of the category dictionary: create, rename, delete entries, and assign/unassign a
 * category to a single operation. Names are unique per party (trimmed, case-sensitive). Deleting a
 * category that is in use first un-assigns it from every operation (back to Uncategorized) so the
 * foreign key never blocks the delete.
 *
 * Every method takes the acting party first and treats another party's entities as nonexistent
 * ("not found"), so ids leaked across parties reveal nothing.
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
    open fun create(partyId: Long, name: String, groupId: Long? = null): Category {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Category name must not be blank" }
        require(categoryRepository.findByPartyIdAndName(partyId, trimmed) == null) {
            "Category '$trimmed' already exists"
        }
        return categoryRepository.save(Category(trimmed, resolveGroup(partyId, groupId), partyId))
    }

    @Transactional
    open fun createAll(partyId: Long, categories: List<Pair<String, Long?>>): List<Category> {
        return categories.map { (name, groupId) -> create(partyId, name, groupId) }
    }

    @Transactional
    open fun rename(partyId: Long, id: Long, name: String): Category = update(partyId, id, name, KeepGroup)

    /** Updates the name and, when [group] is a [SetGroup], the group a category belongs to. */
    @Transactional
    open fun update(partyId: Long, id: Long, name: String, group: GroupChange): Category {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Category name must not be blank" }
        val category = findOwned(partyId, id)
        val clash = categoryRepository.findByPartyIdAndName(partyId, trimmed)
        require(clash == null || clash.id == id) { "Category '$trimmed' already exists" }
        category.name = trimmed
        if (group is SetGroup) category.group = resolveGroup(partyId, group.groupId)
        return categoryRepository.save(category)
    }

    private fun resolveGroup(partyId: Long, groupId: Long?): CategoryGroup? = groupId?.let { id ->
        categoryGroupRepository.findById(id)
            .filter { it.partyId == partyId }
            .orElseThrow { IllegalArgumentException("Group $id not found") }
    }

    @Transactional
    open fun delete(partyId: Long, id: Long) {
        val category = findOwned(partyId, id)
        val affected = bankingOperationRepository.findAllByCategory(category)
        affected.forEach { it.category = null }
        bankingOperationRepository.saveAll(affected)
        categoryRepository.delete(category)
    }

    /** Assigns [categoryId] to the operation, or clears it (back to Uncategorized) when null. */
    @Transactional
    open fun assignToOperation(partyId: Long, operationId: Long, categoryId: Long?): BankingOperation {
        val operation = findOwnedOperation(partyId, operationId)
        operation.category = categoryId?.let { findOwned(partyId, it) }
        operation.needsVerification = false
        val saved = bankingOperationRepository.save(operation)
        if (saved.category != null) {
            autoCategorizationService.indexOperations(listOf(saved))
        }
        return saved
    }

    /** Assigns [categoryId] to every given operation, or clears it (Uncategorized) when null. */
    @Transactional
    open fun assignToOperations(partyId: Long, operationIds: List<Long>, categoryId: Long?): List<BankingOperation> {
        val category = categoryId?.let { findOwned(partyId, it) }
        val operations = findOwnedOperations(partyId, operationIds)
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
    open fun acceptPrediction(partyId: Long, operationId: Long): BankingOperation {
        val operation = findOwnedOperation(partyId, operationId)
        operation.needsVerification = false
        return bankingOperationRepository.save(operation)
    }

    @Transactional
    open fun acceptPredictions(partyId: Long, operationIds: List<Long>): List<BankingOperation> {
        val operations = findOwnedOperations(partyId, operationIds)
        operations.forEach { it.needsVerification = false }
        return bankingOperationRepository.saveAll(operations).toList()
    }

    /** Indexes the party's already-categorized operations into Elasticsearch to train the auto-categorization. */
    @Transactional(readOnly = true)
    open fun syncCategorizedOperationsToSearch(partyId: Long): Int {
        val categorized = bankingOperationRepository.findAllByPartyId(partyId).filter { it.category != null }
        if (categorized.isNotEmpty()) {
            autoCategorizationService.indexOperations(categorized)
        }
        return categorized.size
    }

    private fun findOwned(partyId: Long, id: Long): Category =
        categoryRepository.findById(id)
            .filter { it.partyId == partyId }
            .orElseThrow { IllegalArgumentException("Category $id not found") }

    private fun findOwnedOperation(partyId: Long, id: Long): BankingOperation =
        bankingOperationRepository.findById(id)
            .filter { it.partyId == partyId }
            .orElseThrow { IllegalArgumentException("Operation $id not found") }

    private fun findOwnedOperations(partyId: Long, ids: List<Long>): List<BankingOperation> {
        val operations = bankingOperationRepository.findAllById(ids)
        operations.forEach {
            require(it.partyId == partyId) { "Operation ${it.id} not found" }
        }
        return operations
    }
}
