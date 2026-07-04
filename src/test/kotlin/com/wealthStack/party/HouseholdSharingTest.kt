package com.wealthStack.party

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.wealthStack.TestAuth
import com.wealthStack.bankstatement.BankingOperationRepository
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

/** The whole point of the Party model: a shared household both members can act in. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestAuth::class)
class HouseholdSharingTest {
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

    private fun baseUrl() = "http://localhost:$port"

    private fun partyHeaders(user: String, partyId: Long? = null): HttpHeaders =
        TestAuth.authHeaders(user).apply { partyId?.let { set("X-Party-Id", it.toString()) } }

    @Test
    fun `household members share data and non-owners cannot manage members`() {
        // Both users exist (sign in once).
        TestAuth.rest("house-anna").getForEntity("${baseUrl()}/api/v1/me", Map::class.java)
        TestAuth.rest("house-ben").getForEntity("${baseUrl()}/api/v1/me", Map::class.java)

        // Anna creates the household and adds Ben.
        val household = TestAuth.rest("house-anna").postForEntity(
            "${baseUrl()}/api/v1/parties",
            HttpEntity(mapOf("name" to "Home"), jsonHeaders("house-anna")),
            Map::class.java
        ).body!!
        val householdId = (household["id"] as Number).toLong()

        TestAuth.rest("house-anna").postForEntity(
            "${baseUrl()}/api/v1/parties/$householdId/members",
            HttpEntity(mapOf("email" to "house-ben@test.local", "role" to "MEMBER"), jsonHeaders("house-anna")),
            Map::class.java
        )

        // Anna imports into the household context; Ben sees it there, but not in his personal party.
        importStatement("house-anna", householdId)
        assertThat(operationCount("house-ben", householdId)).isEqualTo(3)
        assertThat(operationCount("house-ben", null)).isEqualTo(0)

        // Ben (MEMBER) may list members but not add one.
        val members = TestAuth.rest("house-ben").exchange(
            "${baseUrl()}/api/v1/parties/$householdId/members",
            HttpMethod.GET,
            HttpEntity(null, partyHeaders("house-ben")),
            List::class.java
        ).body!!
        assertThat(members.size).isEqualTo(2)

        val status = try {
            TestAuth.rest("house-ben").postForEntity(
                "${baseUrl()}/api/v1/parties/$householdId/members",
                HttpEntity(mapOf("email" to "whoever@test.local"), jsonHeaders("house-ben")),
                Map::class.java
            )
            HttpStatus.OK
        } catch (e: HttpClientErrorException) {
            HttpStatus.valueOf(e.statusCode.value())
        }
        assertThat(status).isEqualTo(HttpStatus.FORBIDDEN)
    }

    private fun jsonHeaders(user: String): HttpHeaders =
        TestAuth.authHeaders(user).apply { contentType = MediaType.APPLICATION_JSON }

    private fun importStatement(user: String, partyId: Long?) {
        val body = LinkedMultiValueMap<String, Any>()
        body.add("file", ClassPathResource("mbank-test-statement.csv"))
        body.add("bankName", "mbank")
        val headers = partyHeaders(user, partyId).apply { contentType = MediaType.MULTIPART_FORM_DATA }
        TestAuth.rest(user).postForEntity("${baseUrl()}/api/v1/bank-statements", HttpEntity(body, headers), Map::class.java)
    }

    private fun operationCount(user: String, partyId: Long?): Int {
        val body = TestAuth.rest(user).exchange(
            "${baseUrl()}/api/v1/bank-statements?size=2000",
            HttpMethod.GET,
            HttpEntity(null, partyHeaders(user, partyId)),
            Map::class.java
        ).body!!
        return (body["content"] as List<*>).size
    }
}
