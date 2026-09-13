package com.wealthStack.bankstatement

import org.springframework.transaction.annotation.Transactional

/**
 * Command side of the category-group dictionary: create, rename, and delete groups. Names are
 * unique (trimmed, case-sensitive), mirroring [CategoryService]. Deleting a group that is in use
 * first un-assigns it from every category (back to Ungrouped) so the foreign key never blocks the
 * delete.
 */
open class CategoryGroupService(
    private val categoryGroupRepository: CategoryGroupRepository,
    private val categoryRepository: CategoryRepository
) {

    @Transactional
    open fun create(name: String): CategoryGroup {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Group name must not be blank" }
        require(categoryGroupRepository.findByName(trimmed) == null) { "Group '$trimmed' already exists" }
        return categoryGroupRepository.save(CategoryGroup(trimmed))
    }

    @Transactional
    open fun rename(id: Long, name: String): CategoryGroup {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Group name must not be blank" }
        val group = categoryGroupRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Group $id not found") }
        val clash = categoryGroupRepository.findByName(trimmed)
        require(clash == null || clash.id == id) { "Group '$trimmed' already exists" }
        group.name = trimmed
        return categoryGroupRepository.save(group)
    }

    @Transactional
    open fun delete(id: Long) {
        val group = categoryGroupRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Group $id not found") }
        val affected = categoryRepository.findAllByGroup(group)
        affected.forEach { it.group = null }
        categoryRepository.saveAll(affected)
        categoryGroupRepository.delete(group)
    }
}
