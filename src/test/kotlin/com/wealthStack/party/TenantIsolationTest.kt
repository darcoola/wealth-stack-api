package com.wealthStack.party

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.wealthStack.TestAuth
import com.wealthStack.bankstatement.AccountMappingRepository
import com.wealthStack.bankstatement.BankingOperationRepository
import com.wealthStack.bankstatement.CategoryRepository
import com.wealthStack.bankstatement.search.AutoCategorizationService
import com.wealthStack.bankstatement.search.BankingOperationSearchRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.core.io.ClassPathResource
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpClientErrorException

/** End-to-end proof that two users' data never bleeds across parties. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestAuth::class)
class TenantIsolationTest {
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
    lateinit var categoryRepository: CategoryRepository

    @Autowired
    lateinit var mappingRepository: AccountMappingRepository

    @Autowired
    lateinit var appUserRepository: AppUserRepository

    @BeforeEach
    fun clean() {
        operationRepository.deleteAll()
        categoryRepository.deleteAll()
        mappingRepository.deleteAll()
    }

    private fun baseUrl() = "http://localhost:$port"

    private fun importStatement(user: String) {
        val body = LinkedMultiValueMap<String, Any>()
        body.add("file", ClassPathResource("mbank-test-statement.csv"))
        body.add("bankName", "mbank")
        val headers = HttpHeaders().apply { contentType = MediaType.MULTIPART_FORM_DATA }
        TestAuth.rest(user).postForEntity("${baseUrl()}/api/v1/bank-statements", HttpEntity(body, headers), Map::class.java)
    }

    private fun operationsOf(user: String): List<*> {
        val body = TestAuth.rest(user).getForEntity("${baseUrl()}/api/v1/bank-statements?size=2000", Map::class.java).body!!
        return body["content"] as List<*>
    }

    @Test
    fun `users only see their own operations, both parties can import the same statement`() {
        importStatement("isolation-alice")
        assertThat(operationsOf("isolation-alice").size).isEqualTo(3)
        assertThat(operationsOf("isolation-bob").size).isEqualTo(0)

        // Same file, different party: no unique-constraint collision, both keep 3 rows.
        importStatement("isolation-bob")
        assertThat(operationsOf("isolation-alice").size).isEqualTo(3)
        assertThat(operationsOf("isolation-bob").size).isEqualTo(3)
    }

    @Test
    fun `mutating another party's operation is rejected as not found`() {
        importStatement("isolation-alice")
        val aliceOpId = (operationsOf("isolation-alice").first() as Map<*, *>)["id"].let { (it as Number).toLong() }

        val headers = TestAuth.authHeaders("isolation-bob").apply { contentType = MediaType.APPLICATION_JSON }
        val status = try {
            TestAuth.rest("isolation-bob").exchange(
                "${baseUrl()}/api/v1/bank-statements/operations/$aliceOpId/additional-info",
                HttpMethod.PUT,
                HttpEntity(mapOf("additionalInfo" to "hijacked"), headers),
                Map::class.java
            )
            HttpStatus.OK
        } catch (e: HttpClientErrorException) {
            HttpStatus.valueOf(e.statusCode.value())
        } catch (e: org.springframework.web.client.HttpServerErrorException) {
            HttpStatus.valueOf(e.statusCode.value())
        }
        assertThat(status == HttpStatus.OK).isEqualTo(false)
        assertThat(operationRepository.findById(aliceOpId).get().additionalInfo == "hijacked").isEqualTo(false)
    }

    @Test
    fun `spoofed X-Party-Id header is rejected with 403 by the membership gate`() {
        importStatement("isolation-alice")
        val alicePartyId = appUserRepository.findByKeycloakSub("isolation-alice")!!.personalPartyId
        // Ensure bob exists too.
        TestAuth.rest("isolation-bob").getForEntity("${baseUrl()}/api/v1/me", Map::class.java)

        val headers = TestAuth.authHeaders("isolation-bob").apply { set("X-Party-Id", alicePartyId.toString()) }
        val status = try {
            TestAuth.rest("isolation-bob").exchange(
                "${baseUrl()}/api/v1/bank-statements?size=10",
                HttpMethod.GET,
                HttpEntity(null, headers),
                Map::class.java
            )
            HttpStatus.OK
        } catch (e: HttpClientErrorException) {
            HttpStatus.valueOf(e.statusCode.value())
        }
        assertThat(status).isEqualTo(HttpStatus.FORBIDDEN)
    }

    @Test
    fun `delete-all only wipes the calling party's ledger`() {
        importStatement("isolation-alice")
        importStatement("isolation-bob")

        TestAuth.rest("isolation-bob").exchange(
            "${baseUrl()}/api/v1/bank-statements/operations/all",
            HttpMethod.DELETE,
            HttpEntity(null, TestAuth.authHeaders("isolation-bob")),
            Map::class.java
        )

        assertThat(operationsOf("isolation-bob").size).isEqualTo(0)
        assertThat(operationsOf("isolation-alice").size).isEqualTo(3)
    }
}
