package com.wealthStack.bankstatement

import org.springframework.transaction.annotation.Transactional

/**
 * Command side of the category-group dictionary: create, rename, and delete groups. Names are
 * unique per party (trimmed, case-sensitive), mirroring [CategoryService]. Deleting a group that
 * is in use first un-assigns it from every category (back to Ungrouped) so the foreign key never
 * blocks the delete. Another party's groups are treated as nonexistent ("not found").
 */
open class CategoryGroupService(
    private val categoryGroupRepository: CategoryGroupRepository,
    private val categoryRepository: CategoryRepository
) {

    @Transactional
    open fun create(partyId: Long, name: String): CategoryGroup {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Group name must not be blank" }
        require(categoryGroupRepository.findByPartyIdAndName(partyId, trimmed) == null) {
            "Group '$trimmed' already exists"
        }
        return categoryGroupRepository.save(CategoryGroup(trimmed, partyId))
    }

    @Transactional
    open fun rename(partyId: Long, id: Long, name: String): CategoryGroup {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Group name must not be blank" }
        val group = findOwned(partyId, id)
        val clash = categoryGroupRepository.findByPartyIdAndName(partyId, trimmed)
        require(clash == null || clash.id == id) { "Group '$trimmed' already exists" }
        group.name = trimmed
        return categoryGroupRepository.save(group)
    }

    @Transactional
    open fun delete(partyId: Long, id: Long) {
        val group = findOwned(partyId, id)
        val affected = categoryRepository.findAllByGroup(group)
        affected.forEach { it.group = null }
        categoryRepository.saveAll(affected)
        categoryGroupRepository.delete(group)
    }

    private fun findOwned(partyId: Long, id: Long): CategoryGroup =
        categoryGroupRepository.findById(id)
            .filter { it.partyId == partyId }
            .orElseThrow { IllegalArgumentException("Group $id not found") }
}
