package com.wealthStack.bankstatement.query

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.wealthStack.bankstatement.BankingOperationRepository
import com.wealthStack.bankstatement.CategoryRepository
import com.wealthStack.bankstatement.CategoryService
import com.wealthStack.bankstatement.ManualOperation
import com.wealthStack.bankstatement.ManualOperationsRequest
import com.wealthStack.bankstatement.StatementImporter
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.time.LocalDate

@SpringBootTest
class ReportFinderTest {

    @Autowired
    lateinit var reportFinder: ReportFinder

    @Autowired
    lateinit var categoryService: CategoryService

    @Autowired
    lateinit var importer: StatementImporter

    @Autowired
    lateinit var operationRepository: BankingOperationRepository

    @Autowired
    lateinit var categoryRepository: CategoryRepository

    /**
     * Cleans the tables, then seeds a small fixture spanning two months and covering an income row, a
     * spending row, an Uncategorized row, and a second spending row in the following month.
     */
    @BeforeEach
    fun seed() {
        operationRepository.deleteAll()
        categoryRepository.deleteAll()
        categoryService.create("Salary")
        categoryService.create("Groceries")
        importer.importOperations(
            ManualOperationsRequest(
                bankName = "legacy",
                operations = listOf(
                    op("2024-01-15", "Salary", "5000.00", "Salary"),
                    op("2024-01-20", "Biedronka", "-200.00", "Groceries"),
                    op("2024-01-25", "Cash withdrawal", "-50.00", category = null),
                    op("2024-02-10", "Lidl", "-100.00", "Groceries"),
                )
            )
        )
    }

    private fun op(date: String, description: String, amount: String, category: String?) =
        ManualOperation(
            date = LocalDate.parse(date),
            description = description,
            amount = BigDecimal(amount),
            account = "ACME 111",
            category = category
        )

    private fun bucket(rows: List<MonthlyCategoryTotalDto>, month: String, category: String?) =
        rows.single { it.month == month && it.category == category }

    /** BigDecimal equality is scale-sensitive; compare by value instead. */
    private fun total(rows: List<MonthlyCategoryTotalDto>, month: String, category: String?) =
        bucket(rows, month, category).total.stripTrailingZeros()

    private fun value(amount: String) = BigDecimal(amount).stripTrailingZeros()

    @Test
    fun `all mode returns the net signed sum per bucket`() {
        val rows = reportFinder.categoryMonthlyTotals(AmountMode.ALL)

        assertThat(total(rows, "2024-01", "Salary")).isEqualTo(value("5000"))
        assertThat(total(rows, "2024-01", "Groceries")).isEqualTo(value("-200"))
        assertThat(total(rows, "2024-02", "Groceries")).isEqualTo(value("-100"))
    }

    @Test
    fun `spendings mode returns positive debit magnitudes and ignores income`() {
        val rows = reportFinder.categoryMonthlyTotals(AmountMode.SPENDINGS)

        assertThat(total(rows, "2024-01", "Groceries")).isEqualTo(value("200"))
        assertThat(total(rows, "2024-01", "Salary")).isEqualTo(value("0"))
    }

    @Test
    fun `income mode returns credits only`() {
        val rows = reportFinder.categoryMonthlyTotals(AmountMode.INCOME)

        assertThat(total(rows, "2024-01", "Salary")).isEqualTo(value("5000"))
        assertThat(total(rows, "2024-01", "Groceries")).isEqualTo(value("0"))
    }

    @Test
    fun `uncategorized rows land in a null-category bucket`() {
        val rows = reportFinder.categoryMonthlyTotals(AmountMode.ALL)

        val uncategorized = bucket(rows, "2024-01", null)
        assertThat(uncategorized.categoryId).isNull()
        assertThat(uncategorized.total.stripTrailingZeros()).isEqualTo(value("-50"))

        assertThat(bucket(rows, "2024-01", "Salary").categoryId).isNotNull()
    }
}
