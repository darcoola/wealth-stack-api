package com.wealthStack.bankstatement

import org.springframework.test.context.bean.override.mockito.MockitoBean
import com.wealthStack.bankstatement.search.BankingOperationSearchRepository
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import com.wealthStack.bankstatement.search.AutoCategorizationService

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class CategoryGroupTest {
    @MockitoBean
    lateinit var searchRepository: BankingOperationSearchRepository

    @MockitoBean
    lateinit var elasticsearchOperations: ElasticsearchOperations

    @MockitoBean
    lateinit var autoCategorizationService: AutoCategorizationService

    @Autowired
    lateinit var categoryGroupService: CategoryGroupService

    @Autowired
    lateinit var categoryService: CategoryService

    @Autowired
    lateinit var categoryGroupRepository: CategoryGroupRepository

    @Autowired
    lateinit var categoryRepository: CategoryRepository

    @Autowired
    lateinit var operationRepository: BankingOperationRepository

    @BeforeEach
    fun clean() {
        // The H2 database is shared across test classes: operations another class left behind may
        // still reference categories, so they must go first or the category delete hits the FK.
        operationRepository.deleteAll()
        categoryRepository.deleteAll()
        categoryGroupRepository.deleteAll()
    }

    @Test
    fun `creates renames and rejects duplicate names`() {
        val spending = categoryGroupService.create("Spending")
        assertThat(spending.id).isNotNull()

        categoryGroupService.rename(spending.id!!, "Outgoing")
        assertThat(categoryGroupRepository.findById(spending.id!!).get().name).isEqualTo("Outgoing")

        categoryGroupService.create("Income")
        // Renaming onto an existing name is rejected.
        assertThrows<IllegalArgumentException> { categoryGroupService.rename(spending.id!!, "Income") }
        // Creating a duplicate name is rejected.
        assertThrows<IllegalArgumentException> { categoryGroupService.create("Income") }
    }

    @Test
    fun `a category is created in a group and reads it back`() {
        val group = categoryGroupService.create("Spending")

        val category = categoryService.create("Groceries", group.id)

        assertThat(categoryRepository.findById(category.id!!).get().group?.name).isEqualTo("Spending")
    }

    @Test
    fun `updating a category moves it between groups and can ungroup it`() {
        val spending = categoryGroupService.create("Spending")
        val investments = categoryGroupService.create("Investments")
        val category = categoryService.create("Fund", spending.id)

        categoryService.update(category.id!!, "Fund", SetGroup(investments.id))
        assertThat(categoryRepository.findById(category.id!!).get().group?.name).isEqualTo("Investments")

        // Setting the group to null ungroups the category.
        categoryService.update(category.id!!, "Fund", SetGroup(null))
        assertThat(categoryRepository.findById(category.id!!).get().group).isNull()
    }

    @Test
    fun `deleting a group in use leaves its categories ungrouped`() {
        val group = categoryGroupService.create("Spending")
        val category = categoryService.create("Groceries", group.id)

        categoryGroupService.delete(group.id!!)

        assertThat(categoryGroupRepository.findById(group.id!!).isPresent).isEqualTo(false)
        assertThat(categoryRepository.findById(category.id!!).get().group).isNull()
    }
}
