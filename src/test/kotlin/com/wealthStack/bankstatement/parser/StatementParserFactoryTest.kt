package com.wealthStack.bankstatement.parser

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.messageContains
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.core.io.ClassPathResource

class StatementParserFactoryTest {

    private val factory =
        StatementParserFactory(listOf(MBankCsvParser(), PkoBpCsvParser(), RevolutCsvParser(), ManualCsvParser()))

    private fun bytes(fixture: String) = ClassPathResource(fixture).inputStream.use { it.readBytes() }

    @Test
    fun `detects mBank from file content`() {
        assertThat(factory.detectParser(bytes("mbank-test-statement.csv")).bankName).isEqualTo("mbank")
    }

    @Test
    fun `detects PKO BP from file content despite its windows-1250 encoding`() {
        assertThat(factory.detectParser(bytes("pkobp-test-statement.csv")).bankName).isEqualTo("pkobp")
    }

    @Test
    fun `detects Revolut from file content`() {
        assertThat(factory.detectParser(bytes("revolut-test-statement.csv")).bankName).isEqualTo("revolut")
    }

    @Test
    fun `detects the manual schema from file content`() {
        assertThat(factory.detectParser(bytes("manual-test-statement.csv")).bankName).isEqualTo("manual")
    }

    @Test
    fun `throws when no parser recognizes the content`() {
        val error = assertThrows<IllegalArgumentException> {
            factory.detectParser("nothing,recognizable,here\n1,2,3".toByteArray())
        }
        assertThat(error).messageContains("Could not detect")
    }
}
