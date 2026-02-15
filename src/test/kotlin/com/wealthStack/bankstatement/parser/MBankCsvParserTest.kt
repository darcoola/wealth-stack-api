package com.wealthStack.bankstatement.parser

import assertk.assertThat
import assertk.assertions.*
import com.wealthStack.bankstatement.OperationType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate

class MBankCsvParserTest {

    private val parser = MBankCsvParser()

    private fun loadTestCsv(): String =
        javaClass.getResource("/mbank-test-statement.csv")!!.readText()

    @Test
    fun `parses correct number of operations`() {
        val operations = parser.parse(loadTestCsv(), "test.csv")
        assertThat(operations).hasSize(3)
    }

    @Test
    fun `parses date correctly`() {
        val operations = parser.parse(loadTestCsv(), "test.csv")
        assertThat(operations[0].date).isEqualTo(LocalDate.of(2026, 1, 31))
    }

    @Test
    fun `collapses whitespace in description`() {
        val operations = parser.parse(loadTestCsv(), "test.csv")
        assertThat(operations[0].description).isEqualTo("Orlen ZAKUP PRZY UŻYCIU KARTY - INTERNET")
    }

    @Test
    fun `parses negative amount with decimal`() {
        val operations = parser.parse(loadTestCsv(), "test.csv")
        assertThat(operations[0].amount).isEqualTo(BigDecimal("-135.76"))
        assertThat(operations[0].type).isEqualTo(OperationType.DEBIT)
    }

    @Test
    fun `parses negative amount with thousands separator`() {
        val operations = parser.parse(loadTestCsv(), "test.csv")
        assertThat(operations[1].amount).isEqualTo(BigDecimal("-9000.00"))
        assertThat(operations[1].type).isEqualTo(OperationType.DEBIT)
    }

    @Test
    fun `parses positive amount as CREDIT`() {
        val operations = parser.parse(loadTestCsv(), "test.csv")
        assertThat(operations[2].amount).isEqualTo(BigDecimal("2000.00"))
        assertThat(operations[2].type).isEqualTo(OperationType.CREDIT)
    }

    @Test
    fun `parses account name`() {
        val operations = parser.parse(loadTestCsv(), "test.csv")
        assertThat(operations[0].account).isEqualTo("mKonto Intensive 5611 ... 1026")
    }

    @Test
    fun `parses category`() {
        val operations = parser.parse(loadTestCsv(), "test.csv")
        assertThat(operations[0].category).isEqualTo("Paliwo")
        assertThat(operations[2].category).isEqualTo("Wpływy - inne")
    }

    @Test
    fun `sets source file name on all operations`() {
        val operations = parser.parse(loadTestCsv(), "my-file.csv")
        assertThat(operations).each { it.prop("sourceFileName") { it.sourceFileName }.isEqualTo("my-file.csv") }
    }

    @Test
    fun `sets bank name on all operations`() {
        val operations = parser.parse(loadTestCsv(), "test.csv")
        assertThat(operations).each { it.prop("bankName") { it.bankName }.isEqualTo("mbank") }
    }

    @Test
    fun `throws on missing data header`() {
        val exception = assertThrows<IllegalArgumentException> {
            parser.parse("just some text\nno header here", "test.csv")
        }
        assertThat(exception.message).isEqualTo("Could not find data header line in mBank CSV")
    }
}
