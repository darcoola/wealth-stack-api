package com.wealthStack.bankstatement

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.wealthStack.TestAuth
import com.wealthStack.bankstatement.search.AutoCategorizationService
import com.wealthStack.bankstatement.search.BankingOperationSearchRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.web.client.HttpClientErrorException
import java.math.BigDecimal

/** The Add-operation form: hand-entered cash rows always insert, never fold onto an existing row. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestAuth::class)
class CashOperationTest {
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

    @Autowired
    lateinit var accountMappingRepository: AccountMappingRepository

    @BeforeEach
    fun clean() {
        operationRepository.deleteAll()
        accountMappingRepository.deleteAll()
    }

    private fun post(json: String) = TestAuth.rest().postForEntity(
        "http://localhost:$port/api/v1/bank-statements/operations/manual",
        HttpEntity(json, HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
        Map::class.java
    )

    @Test
    fun `books a cash spend on the cash account and auto-creates its mapping`() {
        val body = post(
            """{ "date": "2026-07-14", "description": "Coffee", "amount": -12.00 }"""
        ).body!!

        assertThat(body["description"]).isEqualTo("Coffee")
        assertThat(body["accountDisplayName"]).isEqualTo("Cash")
        assertThat(body["categoryId"]).isNull()

        val saved = operationRepository.findAll().single()
        assertThat(saved.bankName).isEqualTo("cash")
        assertThat(saved.type).isEqualTo(OperationType.DEBIT)
        assertThat(accountMappingRepository.findAll().single().rawAccount).isEqualTo("Cash")
    }

    @Test
    fun `refuses an identical entry until the user confirms it, then keeps both`() {
        val json = """{ "date": "2026-07-14", "description": "Coffee", "amount": -12.00 }"""
        post(json)

        // Same content again is far more likely a double-submit than a second coffee: 409 + the match.
        val conflict = assertThrows<HttpClientErrorException.Conflict> { post(json) }
        val body = conflict.getResponseBodyAs(Map::class.java)!!
        assertThat((body["duplicates"] as List<*>).size).isEqualTo(1)
        assertThat(operationRepository.findAll().size).isEqualTo(1)

        // Confirmed: it really is a second coffee. Same fingerprint, disambiguated by occurrence.
        post("""{ "date": "2026-07-14", "description": "Coffee", "amount": -12.00, "force": true }""")
        val saved = operationRepository.findAll()
        assertThat(saved.size).isEqualTo(2)
        assertThat(saved.map { it.occurrence }.sorted()).isEqualTo(listOf(0, 1))
    }

    @Test
    fun `a different amount or description is not a duplicate`() {
        post("""{ "date": "2026-07-14", "description": "Coffee", "amount": -12.00 }""")
        post("""{ "date": "2026-07-14", "description": "Coffee", "amount": -14.00 }""")
        post("""{ "date": "2026-07-14", "description": "Cake", "amount": -12.00 }""")

        assertThat(operationRepository.findAll().size).isEqualTo(3)
    }

    @Test
    fun `assigns the chosen category without flagging it for verification`() {
        // Created over HTTP so it lands on the acting party, the one addCashOperation resolves against.
        val name = "Groceries-${System.nanoTime()}"
        val category = TestAuth.rest().postForEntity(
            "http://localhost:$port/api/v1/categories",
            HttpEntity("""{ "name": "$name" }""", HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
            Map::class.java
        ).body!!

        val body = post(
            """{ "date": "2026-07-14", "description": "Market", "amount": -30.00, "categoryId": ${category["id"]} }"""
        ).body!!

        assertThat(body["category"]).isEqualTo(name)
        assertThat(body["needsVerification"]).isEqualTo(false)
        assertThat(operationRepository.findAll().single().category?.id).isNotNull()
    }

    @Test
    fun `rejects a blank description`() {
        val error = assertThrows<HttpClientErrorException.BadRequest> {
            post("""{ "date": "2026-07-14", "description": "  ", "amount": -12.00 }""")
        }
        assertThat(error.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(operationRepository.findAll().size).isEqualTo(0)
    }

    @Test
    fun `records income as a credit`() {
        post("""{ "date": "2026-07-14", "description": "Cash gift", "amount": 200.00 }""")

        val saved = operationRepository.findAll().single()
        assertThat(saved.type).isEqualTo(OperationType.CREDIT)
        assertThat(saved.amount).isEqualTo(BigDecimal("200.00"))
    }
}
