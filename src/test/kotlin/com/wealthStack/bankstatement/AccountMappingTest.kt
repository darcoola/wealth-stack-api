package com.wealthStack.bankstatement

import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AccountMappingTest {

    @LocalServerPort
    var port: Int = 0

    private fun baseUrl() = "http://localhost:$port"
    private val rest = RestTemplate()

    @Test
    fun `mapping applied during import`() {
        // Create a mapping first
        val mappingHeaders = HttpHeaders()
        mappingHeaders.contentType = MediaType.APPLICATION_JSON

        rest.exchange(
            "${baseUrl()}/api/v1/account-mappings",
            HttpMethod.PUT,
            HttpEntity(
                AccountMappingRequest("mKonto Intensive 5611 ... 1026", "mBank ROR"),
                mappingHeaders
            ),
            Map::class.java
        )

        // Import a statement — operations should have displayName set
        val body = LinkedMultiValueMap<String, Any>()
        body.add("file", ClassPathResource("mbank-test-statement.csv"))
        body.add("bankName", "mbank")

        val uploadHeaders = HttpHeaders()
        uploadHeaders.contentType = MediaType.MULTIPART_FORM_DATA

        val importResponse = rest.postForEntity(
            "${baseUrl()}/api/v1/bank-statements",
            HttpEntity(body, uploadHeaders),
            Map::class.java
        )

        assertThat(importResponse.statusCode.value()).isEqualTo(200)
        assertThat(importResponse.body).isNotNull()

        val operations = importResponse.body!!["operations"] as List<*>
        assertThat(operations.map { (it as Map<*, *>)["displayName"] }).each {
            it.isEqualTo("mBank ROR")
        }
        // raw account is preserved
        assertThat(operations.map { (it as Map<*, *>)["account"] }).each {
            it.isEqualTo("mKonto Intensive 5611 ... 1026")
        }
    }

    @Test
    fun `mapping retroactively updates existing operations`() {
        // Import first — operations will have no displayName
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

        // Now create/update a mapping — should set displayName on existing operations
        val mappingHeaders = HttpHeaders()
        mappingHeaders.contentType = MediaType.APPLICATION_JSON

        rest.exchange(
            "${baseUrl()}/api/v1/account-mappings",
            HttpMethod.PUT,
            HttpEntity(
                AccountMappingRequest("mKonto Intensive 5611 ... 1026", "mBank Retroactive"),
                mappingHeaders
            ),
            Map::class.java
        )

        // Verify via GET
        val allOps = rest.getForEntity(
            "${baseUrl()}/api/v1/bank-statements",
            List::class.java
        )
        val ops = allOps.body!!.filterIsInstance<Map<*, *>>()
        val displayNames = ops.map { it["displayName"] }.distinct()
        assertThat(displayNames).each {
            it.isEqualTo("mBank Retroactive")
        }
        // raw account untouched
        val accounts = ops.map { it["account"] }.distinct()
        assertThat(accounts).each {
            it.isEqualTo("mKonto Intensive 5611 ... 1026")
        }
    }

    @Test
    fun `upsert updates existing mapping`() {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON

        // Create
        rest.exchange(
            "${baseUrl()}/api/v1/account-mappings",
            HttpMethod.PUT,
            HttpEntity(AccountMappingRequest("raw-account-1", "Name V1"), headers),
            Map::class.java
        )

        // Update
        val updateResponse = rest.exchange(
            "${baseUrl()}/api/v1/account-mappings",
            HttpMethod.PUT,
            HttpEntity(AccountMappingRequest("raw-account-1", "Name V2"), headers),
            Map::class.java
        )

        assertThat(updateResponse.statusCode.value()).isEqualTo(200)
        assertThat(updateResponse.body!!["displayName"]).isEqualTo("Name V2")

        // Verify via GET
        val allResponse = rest.getForEntity(
            "${baseUrl()}/api/v1/account-mappings",
            List::class.java
        )
        val mappings = allResponse.body!!
        val matching = mappings.filterIsInstance<Map<*, *>>()
            .filter { it["rawAccount"] == "raw-account-1" }
        assertThat(matching.size).isEqualTo(1)
        assertThat(matching[0]["displayName"]).isEqualTo("Name V2")
    }
}
