package com.wealthStack.bankstatement

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
        val groceries = categoryService.create("Groceries")
        assertThat(groceries.id).isNotNull()

        categoryService.rename(groceries.id!!, "Food")
        assertThat(categoryRepository.findById(groceries.id!!).get().name).isEqualTo("Food")

        categoryService.create("Salary")
        // Renaming onto an existing name is rejected.
        assertThrows<IllegalArgumentException> { categoryService.rename(groceries.id!!, "Salary") }
        // Creating a duplicate name is rejected.
        assertThrows<IllegalArgumentException> { categoryService.create("Salary") }
    }

    @Test
    fun `assigns and clears a category on an operation`() {
        importer.importStatement("mbank", "mbank-test-statement.csv", mbankBytes())
        val operation = operationRepository.findAll().first()
        val category = categoryService.create("Fuel")

        categoryService.assignToOperation(operation.id!!, category.id)
        assertThat(operationRepository.findById(operation.id!!).get().category?.name).isEqualTo("Fuel")

        // Passing null clears it back to Uncategorized.
        categoryService.assignToOperation(operation.id!!, null)
        assertThat(operationRepository.findById(operation.id!!).get().category).isNull()
    }

    @Test
    fun `deleting a category in use leaves its operations uncategorized`() {
        importer.importStatement("mbank", "mbank-test-statement.csv", mbankBytes())
        val operation = operationRepository.findAll().first()
        val category = categoryService.create("Fuel")
        categoryService.assignToOperation(operation.id!!, category.id)

        categoryService.delete(category.id!!)

        assertThat(categoryRepository.findById(category.id!!).isPresent).isEqualTo(false)
        assertThat(operationRepository.findById(operation.id!!).get().category).isNull()
    }

    @Test
    fun `re-import of a raw bank statement preserves a manually assigned category`() {
        importer.importStatement("mbank", "mbank-test-statement.csv", mbankBytes())
        val operation = operationRepository.findAll().first()
        val category = categoryService.create("Fuel")
        categoryService.assignToOperation(operation.id!!, category.id)

        // Re-importing the same statement folds onto the existing rows and must NOT reset category.
        importer.importStatement("mbank", "mbank-test-statement.csv", mbankBytes())

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
        categoryService.create("Salary")

        importer.importOperations(manualRequest("Salary"))

        assertThat(operationRepository.findAll().single().category?.name).isEqualTo("Salary")
    }

    @Test
    fun `manual import rejects an unknown category name`() {
        assertThrows<IllegalArgumentException> { importer.importOperations(manualRequest("DoesNotExist")) }
    }

    @Test
    fun `manual re-import overwrites with the file's category`() {
        categoryService.create("Salary")
        categoryService.create("Bonus")

        importer.importOperations(manualRequest("Salary"))
        // The same row re-imported with a different category: the file is the source of truth.
        importer.importOperations(manualRequest("Bonus"))

        assertThat(operationRepository.findAll().single().category?.name).isEqualTo("Bonus")
    }
}
