package com.wealthStack.bankstatement.parser

import assertk.assertThat
import assertk.assertions.*
import com.wealthStack.bankstatement.OperationType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate

class PkoBpCsvParserTest {

    private val parser = PkoBpCsvParser()

    private fun loadTestCsv(): String =
        javaClass.getResource("/pkobp-test-statement.csv")!!.readBytes().toString(parser.charset)

    @Test
    fun `parses correct number of operations`() {
        val operations = parser.parse(loadTestCsv(), "test.csv")
        assertThat(operations).hasSize(4)
    }

    @Test
    fun `parses ISO date correctly`() {
        val operations = parser.parse(loadTestCsv(), "test.csv")
        assertThat(operations[0].date).isEqualTo(LocalDate.of(2026, 5, 31))
    }

    @Test
    fun `parses negative amount as DEBIT`() {
        val operations = parser.parse(loadTestCsv(), "test.csv")
        assertThat(operations[0].amount).isEqualTo(BigDecimal("-50.01"))
        assertThat(operations[0].type).isEqualTo(OperationType.DEBIT)
    }

    @Test
    fun `parses positive amount as CREDIT`() {
        val operations = parser.parse(loadTestCsv(), "test.csv")
        assertThat(operations[2].amount).isEqualTo(BigDecimal("2000.00"))
        assertThat(operations[2].type).isEqualTo(OperationType.CREDIT)
    }

    @Test
    fun `decodes Windows-1250 Polish characters in description`() {
        val operations = parser.parse(loadTestCsv(), "test.csv")
        assertThat(operations[0].description).contains("Tytuł")
        assertThat(operations[2].description).contains("PASAŻ")
    }

    @Test
    fun `leaves imported operations uncategorized`() {
        val operations = parser.parse(loadTestCsv(), "test.csv")
        assertThat(operations).each { it.prop("category") { it.category }.isNull() }
    }

    @Test
    fun `keeps commas inside quoted description fields`() {
        val operations = parser.parse(loadTestCsv(), "test.csv")
        assertThat(operations[1].amount).isEqualTo(BigDecimal("-2214.25"))
        assertThat(operations[1].description).contains("ODSETKI: 2214,25")
    }

    @Test
    fun `joins multiple description columns`() {
        val operations = parser.parse(loadTestCsv(), "test.csv")
        assertThat(operations[0].description).contains("Lokalizacja:")
        assertThat(operations[0].description).contains("Numer karty:")
    }

    @Test
    fun `uses card number as account for card operations`() {
        val operations = parser.parse(loadTestCsv(), "test.csv")
        assertThat(operations[0].account).isEqualTo("425125******6487")
    }

    @Test
    fun `uses sender account as account for transfers`() {
        val operations = parser.parse(loadTestCsv(), "test.csv")
        assertThat(operations[2].account).isEqualTo("96 1240 1109 1111 0010 7276 0171")
    }

    @Test
    fun `falls back to empty account when none present`() {
        val operations = parser.parse(loadTestCsv(), "test.csv")
        assertThat(operations[1].account).isEqualTo("")
    }

    @Test
    fun `sets source file name on all operations`() {
        val operations = parser.parse(loadTestCsv(), "my-file.csv")
        assertThat(operations).each { it.prop("sourceFileName") { it.sourceFileName }.isEqualTo("my-file.csv") }
    }

    @Test
    fun `sets bank name on all operations`() {
        val operations = parser.parse(loadTestCsv(), "test.csv")
        assertThat(operations).each { it.prop("bankName") { it.bankName }.isEqualTo("pkobp") }
    }

    @Test
    fun `throws on missing data header`() {
        val exception = assertThrows<IllegalArgumentException> {
            parser.parse("just some text\nno header here", "test.csv")
        }
        assertThat(exception.message).isEqualTo("Could not find data header line in PKO BP CSV")
    }
}
