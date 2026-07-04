package com.wealthStack.bankstatement

import org.springframework.test.context.bean.override.mockito.MockitoBean
import com.wealthStack.bankstatement.search.BankingOperationSearchRepository
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import com.wealthStack.bankstatement.search.AutoCategorizationService

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.context.annotation.Import
import com.wealthStack.TestAuth

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestAuth::class)
class ManualOperationsImportTest {
    @MockitoBean
    lateinit var searchRepository: BankingOperationSearchRepository

    @MockitoBean
    lateinit var elasticsearchOperations: ElasticsearchOperations

    @MockitoBean
    lateinit var autoCategorizationService: AutoCategorizationService


    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var operationRepository: BankingOperationRepository

    @BeforeEach
    fun clean() = operationRepository.deleteAll()

    private fun post(json: String): Map<*, *>? {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        return TestAuth.rest().postForEntity(
            "http://localhost:$port/api/v1/bank-statements/operations",
            HttpEntity(json, headers),
            Map::class.java
        ).body
    }

    @Test
    fun `imports prepared operation rows and dedups on re-send`() {
        val payload = """
            {
              "bankName": "legacy",
              "source": "2024-history",
              "operations": [
                { "date": "2024-01-15", "description": "Salary", "amount": 5000.00, "account": "ACME 111" },
                { "date": "2024-01-16", "description": "Groceries", "amount": -120.50, "account": "ACME 111" }
              ]
            }
        """.trimIndent()

        val first = post(payload)
        assertThat(first!!["operationsImported"]).isEqualTo(2)
        assertThat(first["operationsOverwritten"]).isEqualTo(0)
        assertThat(operationRepository.findAll().size).isEqualTo(2)

        // Same content again folds onto the existing rows instead of duplicating.
        val second = post(payload)
        assertThat(second!!["operationsImported"]).isEqualTo(0)
        assertThat(second["operationsOverwritten"]).isEqualTo(2)
        assertThat(operationRepository.findAll().size).isEqualTo(2)
    }
}
