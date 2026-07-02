package com.wealthStack.bankstatement.parser

import org.springframework.test.context.bean.override.mockito.MockitoBean
import com.wealthStack.bankstatement.search.BankingOperationSearchRepository
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import com.wealthStack.bankstatement.search.AutoCategorizationService

import assertk.assertThat
import assertk.assertions.*
import com.wealthStack.bankstatement.OperationType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate

class ManualCsvParserTest {
    @MockitoBean
    lateinit var searchRepository: BankingOperationSearchRepository

    @MockitoBean
    lateinit var elasticsearchOperations: ElasticsearchOperations

    @MockitoBean
    lateinit var autoCategorizationService: AutoCategorizationService


    private val parser = ManualCsvParser()

    private fun loadTestCsv(): String =
        javaClass.getResource("/manual-test-statement.csv")!!.readText()

    @Test
    fun `parses every data row`() {
        val operations = parser.parse(loadTestCsv(), "test.csv")
        assertThat(operations).hasSize(3)
    }

    @Test
    fun `parses required fields`() {
        val op = parser.parse(loadTestCsv(), "test.csv")[0]
        assertThat(op.date).isEqualTo(LocalDate.of(2024, 1, 15))
        assertThat(op.bankName).isEqualTo("legacy")
        assertThat(op.account).isEqualTo("ACME 111")
        assertThat(op.description).isEqualTo("Salary")
        assertThat(op.amount).isEqualTo(BigDecimal("5000.00"))
        assertThat(op.type).isEqualTo(OperationType.CREDIT)
    }

    @Test
    fun `derives DEBIT from negative amount and keeps quoted commas in description`() {
        val op = parser.parse(loadTestCsv(), "test.csv")[1]
        assertThat(op.description).isEqualTo("Groceries, weekly")
        assertThat(op.amount).isEqualTo(BigDecimal("-120.50"))
        assertThat(op.type).isEqualTo(OperationType.DEBIT)
    }

    @Test
    fun `parses European amounts with comma decimal and space thousands separators`() {
        val header = "date,bankName,account,description,amount"
        val operations = parser.parse(
            "$header\n" +
                "2025-01-02,manual,Millenium ROR,Składka,\"-4,98\"\n" +
                "2025-01-03,manual,Millenium ROR,Przelew,\"-101 933,26\"\n" +
                "2025-01-04,manual,Millenium ROR,Wynagrodzenie,\"9 942,61\"",
            "test.csv"
        )
        assertThat(operations[0].amount).isEqualTo(BigDecimal("-4.98"))
        assertThat(operations[1].amount).isEqualTo(BigDecimal("-101933.26"))
        assertThat(operations[2].amount).isEqualTo(BigDecimal("9942.61"))
    }

    @Test
    fun `parses a quoted field that spans multiple physical lines`() {
        val header = "date,bankName,account,description,amount"
        val operations = parser.parse(
            "$header\r\n" +
                "2025-11-02,manual,Revolut,\"Gross interest\r\nEarned on 2025/11/02\",\"0,01\"\r\n" +
                "2025-11-03,manual,Revolut,Salary,\"9,89\"",
            "test.csv"
        )
        assertThat(operations).hasSize(2)
        assertThat(operations[0].description).isEqualTo("Gross interest\r\nEarned on 2025/11/02")
        assertThat(operations[0].amount).isEqualTo(BigDecimal("0.01"))
        assertThat(operations[1].description).isEqualTo("Salary")
    }

    @Test
    fun `optional accountDisplayName defaults when blank`() {
        val operations = parser.parse(loadTestCsv(), "test.csv")
        assertThat(operations[0].accountDisplayName).isEqualTo("Old Employer")
        assertThat(operations[1].accountDisplayName).isNull()
    }

    @Test
    fun `optional additionalInfo is captured and defaults to null when blank`() {
        val operations = parser.parse(loadTestCsv(), "test.csv")
        assertThat(operations[0].additionalInfo).isNull()
        assertThat(operations[1].additionalInfo).isEqualTo("weekly shop at Lidl")
    }

    @Test
    fun `captures the category name for the importer to resolve`() {
        val operations = parser.parse(loadTestCsv(), "test.csv")
        assertThat(operations[0].categoryName).isEqualTo("income")
        assertThat(operations[1].categoryName).isNull()   // blank category column
        assertThat(operations[2].categoryName).isEqualTo("refund")
        // The parser only captures the name; the category relation stays unresolved until import.
        assertThat(operations).each { it.prop("category") { it.category }.isNull() }
    }

    @Test
    fun `allows mixed banks across rows`() {
        val operations = parser.parse(loadTestCsv(), "test.csv")
        assertThat(operations[2].bankName).isEqualTo("other-bank")
    }

    @Test
    fun `throws when a required column is missing from the header`() {
        val exception = assertThrows<IllegalArgumentException> {
            parser.parse("date,account,description,amount\n2024-01-01,A,Hi,1.00", "test.csv")
        }
        assertThat(exception.message).isNotNull().contains("missing required column 'bankname'")
    }
}
