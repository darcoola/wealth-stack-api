package com.wealthStack.bankstatement.query

import org.springframework.test.context.bean.override.mockito.MockitoBean
import com.wealthStack.bankstatement.search.BankingOperationSearchRepository
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import com.wealthStack.bankstatement.search.AutoCategorizationService

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.wealthStack.bankstatement.BankingOperationRepository
import com.wealthStack.bankstatement.CategoryGroupRepository
import com.wealthStack.bankstatement.CategoryGroupService
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
    @MockitoBean
    lateinit var searchRepository: BankingOperationSearchRepository

    @MockitoBean
    lateinit var elasticsearchOperations: ElasticsearchOperations

    @MockitoBean
    lateinit var autoCategorizationService: AutoCategorizationService


    @Autowired
    lateinit var reportFinder: ReportFinder

    @Autowired
    lateinit var categoryService: CategoryService

    @Autowired
    lateinit var categoryGroupService: CategoryGroupService

    @Autowired
    lateinit var importer: StatementImporter

    @Autowired
    lateinit var operationRepository: BankingOperationRepository

    @Autowired
    lateinit var categoryRepository: CategoryRepository

    @Autowired
    lateinit var categoryGroupRepository: CategoryGroupRepository

    /**
     * Cleans the tables, then seeds a small fixture spanning two months and covering an income row, a
     * spending row, an Uncategorized row, and a second spending row in the following month.
     */
    @BeforeEach
    fun seed() {
        operationRepository.deleteAll()
        categoryRepository.deleteAll()
        categoryGroupRepository.deleteAll()
        val income = categoryGroupService.create("Income")
        val spending = categoryGroupService.create("Spending")
        categoryService.create("Salary", income.id)
        categoryService.create("Groceries", spending.id)
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
    fun `totals are the raw signed sum per bucket, not split by debit or credit`() {
        val rows = reportFinder.categoryMonthlyTotals()

        // Income category sums positive, spending categories sum negative — both taken as-is.
        assertThat(total(rows, "2024-01", "Salary")).isEqualTo(value("5000"))
        assertThat(total(rows, "2024-01", "Groceries")).isEqualTo(value("-200"))
        assertThat(total(rows, "2024-02", "Groceries")).isEqualTo(value("-100"))
    }

    @Test
    fun `each bucket carries its category group so the frontend can split by group`() {
        val rows = reportFinder.categoryMonthlyTotals()

        assertThat(bucket(rows, "2024-01", "Salary").groupName).isEqualTo("Income")
        assertThat(bucket(rows, "2024-01", "Groceries").groupName).isEqualTo("Spending")
    }

    @Test
    fun `uncategorized rows land in a null-category, null-group bucket`() {
        val rows = reportFinder.categoryMonthlyTotals()

        val uncategorized = bucket(rows, "2024-01", null)
        assertThat(uncategorized.categoryId).isNull()
        assertThat(uncategorized.groupId).isNull()
        assertThat(uncategorized.total.stripTrailingZeros()).isEqualTo(value("-50"))

        assertThat(bucket(rows, "2024-01", "Salary").categoryId).isNotNull()
    }
}
