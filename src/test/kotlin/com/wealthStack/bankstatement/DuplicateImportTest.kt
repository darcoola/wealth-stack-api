package com.wealthStack.bankstatement

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.core.io.ClassPathResource

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class DuplicateImportTest {

    @Autowired
    lateinit var importer: StatementImporter

    @Autowired
    lateinit var operationRepository: BankingOperationRepository

    @BeforeEach
    fun clean() = operationRepository.deleteAll()

    private fun mbankBytes() = ClassPathResource("mbank-test-statement.csv").inputStream.use { it.readBytes() }

    @Test
    fun `re-importing the same statement overwrites instead of duplicating`() {
        val first = importer.importStatement("mbank", "mbank-test-statement.csv", mbankBytes())
        assertThat(first.operationsImported).isEqualTo(3)
        assertThat(first.operationsOverwritten).isEqualTo(0)
        assertThat(operationRepository.findAll()).hasSize(3)

        val second = importer.importStatement("mbank", "mbank-test-statement.csv", mbankBytes())
        assertThat(second.operationsImported).isEqualTo(0)
        assertThat(second.operationsOverwritten).isEqualTo(3)
        // No duplicates: the second import folded onto the existing rows.
        assertThat(operationRepository.findAll()).hasSize(3)
    }

    @Test
    fun `overwrite keeps a single row per operation and updates its source file`() {
        importer.importStatement("mbank", "original.csv", mbankBytes())
        importer.importStatement("mbank", "re-export.csv", mbankBytes())

        val all = operationRepository.findAll()
        assertThat(all).hasSize(3)
        // Overwrite copied the new provenance onto the existing rows.
        all.forEach { assertThat(it.sourceFileName).isEqualTo("re-export.csv") }
    }
}
