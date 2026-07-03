package com.wealthStack.bankstatement.parser

import assertk.assertThat
import assertk.assertions.*
import com.wealthStack.bankstatement.OperationType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate

class RevolutCsvParserTest {

    private val parser = RevolutCsvParser()

    private fun loadTestCsv(): String =
        javaClass.getResource("/revolut-test-statement.csv")!!.readBytes().toString(parser.charset)

    @Test
    fun `skips non-completed rows and parses the rest`() {
        val operations = parser.parse(loadTestCsv(), "test.csv")
        // 5 data rows, one of which is COFNIĘTO (reverted) and must be dropped.
        assertThat(operations).hasSize(4)
    }

    @Test
    fun `parses date from the started timestamp`() {
        val operations = parser.parse(loadTestCsv(), "test.csv")
        assertThat(operations[0].date).isEqualTo(LocalDate.of(2026, 1, 2))
    }

    @Test
    fun `parses positive amount as CREDIT`() {
        val operations = parser.parse(loadTestCsv(), "test.csv")
        assertThat(operations[0].amount).isEqualTo(BigDecimal("80000.00"))
        assertThat(operations[0].type).isEqualTo(OperationType.CREDIT)
    }

    @Test
    fun `parses negative amount as DEBIT`() {
        val operations = parser.parse(loadTestCsv(), "test.csv")
        assertThat(operations[1].amount).isEqualTo(BigDecimal("-80000.00"))
        assertThat(operations[1].type).isEqualTo(OperationType.DEBIT)
    }

    @Test
    fun `uses the product pocket as the account`() {
        val operations = parser.parse(loadTestCsv(), "test.csv")
        assertThat(operations[0].account).isEqualTo("Bieżące")
        assertThat(operations[3].account).isEqualTo("Oszczędności")
    }

    @Test
    fun `keeps and trims quoted description fields`() {
        val operations = parser.parse(loadTestCsv(), "test.csv")
        assertThat(operations[2].description).isEqualTo("Przelew na telefon BLIK")
    }

    @Test
    fun `decodes UTF-8 Polish characters in description`() {
        val operations = parser.parse(loadTestCsv(), "test.csv")
        assertThat(operations[0].description).isEqualTo("Zasilenie o *8174")
    }

    @Test
    fun `leaves imported operations uncategorized`() {
        val operations = parser.parse(loadTestCsv(), "test.csv")
        assertThat(operations).each { it.prop("category") { it.category }.isNull() }
    }

    @Test
    fun `sets source file name on all operations`() {
        val operations = parser.parse(loadTestCsv(), "my-file.csv")
        assertThat(operations).each { it.prop("sourceFileName") { it.sourceFileName }.isEqualTo("my-file.csv") }
    }

    @Test
    fun `sets bank name on all operations`() {
        val operations = parser.parse(loadTestCsv(), "test.csv")
        assertThat(operations).each { it.prop("bankName") { it.bankName }.isEqualTo("revolut") }
    }

    @Test
    fun `throws on missing data header`() {
        val exception = assertThrows<IllegalArgumentException> {
            parser.parse("just some text\nno header here", "test.csv")
        }
        assertThat(exception.message).isEqualTo("Could not find data header line in Revolut CSV")
    }
}
