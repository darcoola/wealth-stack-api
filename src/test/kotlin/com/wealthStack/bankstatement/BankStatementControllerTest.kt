package com.wealthStack.bankstatement

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class BankStatementControllerTest {

    @LocalServerPort
    var port: Int = 0

    private fun baseUrl() = "http://localhost:$port"

    @Test
    fun `imports mbank statement successfully`() {
        val body = LinkedMultiValueMap<String, Any>()
        body.add("file", ClassPathResource("mbank-test-statement.csv"))
        body.add("bankName", "mbank")

        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA

        val response = RestTemplate().postForEntity(
            "${baseUrl()}/api/v1/bank-statements",
            HttpEntity(body, headers),
            Map::class.java
        )

        assertThat(response.statusCode.value()).isEqualTo(200)
        assertThat(response.body).isNotNull()
        assertThat(response.body!!["operationsImported"]).isEqualTo(3)
        assertThat(response.body!!["bankName"]).isEqualTo("mbank")
        assertThat(response.body!!["fileName"]).isEqualTo("mbank-test-statement.csv")
    }

    @Test
    fun `returns 400 for unsupported bank`() {
        val body = LinkedMultiValueMap<String, Any>()
        body.add("file", ClassPathResource("mbank-test-statement.csv"))
        body.add("bankName", "unsupported")

        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA

        try {
            RestTemplate().postForEntity(
                "${baseUrl()}/api/v1/bank-statements",
                HttpEntity(body, headers),
                Map::class.java
            )
        } catch (e: org.springframework.web.client.HttpClientErrorException) {
            assertThat(e.statusCode.value()).isEqualTo(400)
            assertThat(e.responseBodyAsString).contains("error")
            return
        }
        throw AssertionError("Expected 400 Bad Request")
    }
}
