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
import org.springframework.core.io.ClassPathResource
import java.math.BigDecimal
import java.time.LocalDate

@SpringBootTest
class CategoryTest {
    @MockitoBean
    lateinit var searchRepository: BankingOperationSearchRepository

    @MockitoBean
    lateinit var elasticsearchOperations: ElasticsearchOperations

    @MockitoBean
    lateinit var autoCategorizationService: AutoCategorizationService


    @Autowired
    lateinit var categoryService: CategoryService

    @Autowired
    lateinit var categoryRepository: CategoryRepository

    @Autowired
    lateinit var operationRepository: BankingOperationRepository

    @Autowired
    lateinit var importer: StatementImporter

    @BeforeEach
    fun clean() {
        operationRepository.deleteAll()
        categoryRepository.deleteAll()
    }

    private fun mbankBytes() = ClassPathResource("mbank-test-statement.csv").inputStream.use { it.readBytes() }

    @Test
    fun `creates renames and rejects duplicate names`() {
        val groceries = categoryService.create(1L,"Groceries")
        assertThat(groceries.id).isNotNull()

        categoryService.rename(1L,groceries.id!!, "Food")
        assertThat(categoryRepository.findById(groceries.id!!).get().name).isEqualTo("Food")

        categoryService.create(1L,"Salary")
        // Renaming onto an existing name is rejected.
        assertThrows<IllegalArgumentException> { categoryService.rename(1L,groceries.id!!, "Salary") }
        // Creating a duplicate name is rejected.
        assertThrows<IllegalArgumentException> { categoryService.create(1L,"Salary") }
    }

    @Test
    fun `assigns and clears a category on an operation`() {
        importer.importStatement(1L, "mbank", "mbank-test-statement.csv", mbankBytes())
        val operation = operationRepository.findAll().first()
        val category = categoryService.create(1L,"Fuel")

        categoryService.assignToOperation(1L,operation.id!!, category.id)
        assertThat(operationRepository.findById(operation.id!!).get().category?.name).isEqualTo("Fuel")

        // Passing null clears it back to Uncategorized.
        categoryService.assignToOperation(1L,operation.id!!, null)
        assertThat(operationRepository.findById(operation.id!!).get().category).isNull()
    }

    @Test
    fun `deleting a category in use leaves its operations uncategorized`() {
        importer.importStatement(1L, "mbank", "mbank-test-statement.csv", mbankBytes())
        val operation = operationRepository.findAll().first()
        val category = categoryService.create(1L,"Fuel")
        categoryService.assignToOperation(1L,operation.id!!, category.id)

        categoryService.delete(1L,category.id!!)

        assertThat(categoryRepository.findById(category.id!!).isPresent).isEqualTo(false)
        assertThat(operationRepository.findById(operation.id!!).get().category).isNull()
    }

    @Test
    fun `re-import of a raw bank statement preserves a manually assigned category`() {
        importer.importStatement(1L, "mbank", "mbank-test-statement.csv", mbankBytes())
        val operation = operationRepository.findAll().first()
        val category = categoryService.create(1L,"Fuel")
        categoryService.assignToOperation(1L,operation.id!!, category.id)

        // Re-importing the same statement folds onto the existing rows and must NOT reset category.
        importer.importStatement(1L, "mbank", "mbank-test-statement.csv", mbankBytes())

        assertThat(operationRepository.findById(operation.id!!).get().category?.name).isEqualTo("Fuel")
    }

    private fun manualRequest(category: String?) = ManualOperationsRequest(
        bankName = "legacy",
        operations = listOf(
            ManualOperation(
                date = LocalDate.parse("2024-01-15"),
                description = "Salary",
                amount = BigDecimal("5000.00"),
                account = "ACME 111",
                category = category
            )
        )
    )

    @Test
    fun `manual import assigns an existing dictionary category by name`() {
        categoryService.create(1L,"Salary")

        importer.importOperations(1L, manualRequest("Salary"))

        assertThat(operationRepository.findAll().single().category?.name).isEqualTo("Salary")
    }

    @Test
    fun `manual import rejects an unknown category name`() {
        assertThrows<IllegalArgumentException> { importer.importOperations(1L, manualRequest("DoesNotExist")) }
    }

    @Test
    fun `manual re-import overwrites with the file's category`() {
        categoryService.create(1L,"Salary")
        categoryService.create(1L,"Bonus")

        importer.importOperations(1L, manualRequest("Salary"))
        // The same row re-imported with a different category: the file is the source of truth.
        importer.importOperations(1L, manualRequest("Bonus"))

        assertThat(operationRepository.findAll().single().category?.name).isEqualTo("Bonus")
    }
}
