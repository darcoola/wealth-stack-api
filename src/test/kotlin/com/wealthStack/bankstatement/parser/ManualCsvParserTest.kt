package com.wealthStack.bankstatement.parser

import assertk.assertThat
import assertk.assertions.*
import com.wealthStack.bankstatement.OperationType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate

class ManualCsvParserTest {

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
    fun `optional columns default when blank`() {
        val operations = parser.parse(loadTestCsv(), "test.csv")
        assertThat(operations[0].displayName).isEqualTo("Old Employer")
        assertThat(operations[0].category).isEqualTo("income")
        assertThat(operations[1].displayName).isNull()
        assertThat(operations[1].category).isEqualTo("")
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
