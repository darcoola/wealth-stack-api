package com.wealthStack.bankstatement

import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.context.annotation.Import
import org.springframework.util.LinkedMultiValueMap
import com.wealthStack.TestAuth

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestAuth::class)
class AccountMappingTest {
    @MockitoBean
    lateinit var searchRepository: com.wealthStack.bankstatement.search.BankingOperationSearchRepository

    @MockitoBean
    lateinit var elasticsearchOperations: org.springframework.data.elasticsearch.core.ElasticsearchOperations

    @MockitoBean
    lateinit var autoCategorizationService: com.wealthStack.bankstatement.search.AutoCategorizationService


    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var operationRepository: BankingOperationRepository

    @Autowired
    lateinit var mappingRepository: AccountMappingRepository

    @BeforeEach
    fun clean() {
        operationRepository.deleteAll()
        mappingRepository.deleteAll()
    }

    private fun baseUrl() = "http://localhost:$port"
    private val rest = TestAuth.rest()

    private fun createMapping(rawAccount: String, displayName: String): Map<*, *> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        return rest.postForEntity(
            "${baseUrl()}/api/v1/account-mappings",
            HttpEntity(AccountMappingRequest(rawAccount, displayName), headers),
            Map::class.java
        ).body!!
    }

    private fun importMbankStatement() {
        val body = LinkedMultiValueMap<String, Any>()
        body.add("file", ClassPathResource("mbank-test-statement.csv"))
        body.add("bankName", "mbank")

        val uploadHeaders = HttpHeaders()
        uploadHeaders.contentType = MediaType.MULTIPART_FORM_DATA

        rest.postForEntity(
            "${baseUrl()}/api/v1/bank-statements",
            HttpEntity(body, uploadHeaders),
            Map::class.java
        )
    }

    /**
     * The read endpoint returns a Spring Data [org.springframework.data.domain.Page] (serialized as
     * `{ content: [...], page: {...} }`), not a bare array, so pull the rows out of `content`. A
     * large page size keeps this a "fetch all operations" helper.
     */
    /** Looks up the id of the (possibly auto-created) mapping for a raw account. */
    private fun mappingId(rawAccount: String): Long {
        val body = rest.getForEntity("${baseUrl()}/api/v1/account-mappings", List::class.java).body!!
        return body.filterIsInstance<Map<*, *>>()
            .first { it["rawAccount"] == rawAccount }["id"]
            .let { (it as Number).toLong() }
    }

    private fun fetchOperations(): List<Map<*, *>> {
        val body = rest.getForEntity(
            "${baseUrl()}/api/v1/bank-statements?size=2000",
            Map::class.java
        ).body!!
        return (body["content"] as List<*>).filterIsInstance<Map<*, *>>()
    }

    @Test
    fun `mapping applied during import`() {
        createMapping("mKonto Intensive 5611 ... 1026", "mBank ROR")
        importMbankStatement()

        val operations = fetchOperations()
        assertThat(operations.map { it["accountDisplayName"] }).each {
            it.isEqualTo("mBank ROR")
        }
        // raw account is preserved
        assertThat(operations.map { it["account"] }).each {
            it.isEqualTo("mKonto Intensive 5611 ... 1026")
        }
    }

    @Test
    fun `import auto-creates a mapping for unmapped accounts, defaulting the name to the raw account`() {
        // No mapping exists up front; the import must connect every operation to one.
        importMbankStatement()

        val ops = fetchOperations()
        // display name defaults to the raw account
        assertThat(ops.map { it["accountDisplayName"] }.distinct()).each {
            it.isEqualTo("mKonto Intensive 5611 ... 1026")
        }

        val mappings = rest.getForEntity("${baseUrl()}/api/v1/account-mappings", List::class.java)
            .body!!.filterIsInstance<Map<*, *>>()
            .filter { it["rawAccount"] == "mKonto Intensive 5611 ... 1026" }
        assertThat(mappings.size).isEqualTo(1)
        assertThat(mappings[0]["displayName"]).isEqualTo("mKonto Intensive 5611 ... 1026")
    }

    @Test
    fun `editing the auto-created mapping updates existing operations`() {
        importMbankStatement()

        val id = mappingId("mKonto Intensive 5611 ... 1026")
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        rest.exchange(
            "${baseUrl()}/api/v1/account-mappings/$id",
            HttpMethod.PUT,
            HttpEntity(AccountMappingRequest("mKonto Intensive 5611 ... 1026", "mBank Retroactive"), headers),
            Map::class.java
        )

        val ops = fetchOperations()
        assertThat(ops.map { it["accountDisplayName"] }.distinct()).each {
            it.isEqualTo("mBank Retroactive")
        }
        // raw account untouched
        assertThat(ops.map { it["account"] }.distinct()).each {
            it.isEqualTo("mKonto Intensive 5611 ... 1026")
        }
    }

    @Test
    fun `update changes an existing mapping by id and re-applies the new name`() {
        createMapping("mKonto Intensive 5611 ... 1026", "Name V1")
        importMbankStatement()
        val id = mappingId("mKonto Intensive 5611 ... 1026")

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val updateResponse = rest.exchange(
            "${baseUrl()}/api/v1/account-mappings/$id",
            HttpMethod.PUT,
            HttpEntity(AccountMappingRequest("mKonto Intensive 5611 ... 1026", "Name V2"), headers),
            Map::class.java
        )

        assertThat(updateResponse.statusCode.value()).isEqualTo(200)
        assertThat(updateResponse.body!!["displayName"]).isEqualTo("Name V2")

        // operations reflect the renamed mapping
        assertThat(fetchOperations().map { it["accountDisplayName"] }.distinct()).each {
            it.isEqualTo("Name V2")
        }
    }

    @Test
    fun `delete removes the mapping and reverts operations to the raw account`() {
        importMbankStatement()
        val id = mappingId("mKonto Intensive 5611 ... 1026")

        rest.delete("${baseUrl()}/api/v1/account-mappings/$id")

        // accountDisplayName cleared, so the read model falls back to the raw account
        assertThat(fetchOperations().map { it["accountDisplayName"] }).each {
            it.isEqualTo("mKonto Intensive 5611 ... 1026")
        }

        val mappings = rest.getForEntity(
            "${baseUrl()}/api/v1/account-mappings",
            List::class.java
        )
        assertThat(mappings.body).isNotNull()
        assertThat(mappings.body!!.size).isEqualTo(0)
    }

    @Test
    fun `listing returns mappings with id, raw account, and display name`() {
        createMapping("raw-account-1", "Name V1")

        val allResponse = rest.getForEntity(
            "${baseUrl()}/api/v1/account-mappings",
            List::class.java
        )
        val matching = allResponse.body!!.filterIsInstance<Map<*, *>>()
            .filter { it["rawAccount"] == "raw-account-1" }
        assertThat(matching.size).isEqualTo(1)
        assertThat(matching[0]["displayName"]).isEqualTo("Name V1")
        assertThat(matching[0]["id"]).isNotNull()
    }
}
