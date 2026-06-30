package com.wealthStack.bankstatement

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
import org.springframework.web.client.RestTemplate

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ManualOperationsImportTest {

    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var operationRepository: BankingOperationRepository

    @BeforeEach
    fun clean() = operationRepository.deleteAll()

    private fun post(json: String): Map<*, *>? {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        return RestTemplate().postForEntity(
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
