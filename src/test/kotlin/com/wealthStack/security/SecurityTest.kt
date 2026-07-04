package com.wealthStack.security

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.wealthStack.TestAuth
import com.wealthStack.bankstatement.search.AutoCategorizationService
import com.wealthStack.bankstatement.search.BankingOperationSearchRepository
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestAuth::class)
class SecurityTest {
    @MockitoBean
    lateinit var searchRepository: BankingOperationSearchRepository

    @MockitoBean
    lateinit var elasticsearchOperations: ElasticsearchOperations

    @MockitoBean
    lateinit var autoCategorizationService: AutoCategorizationService

    @LocalServerPort
    var port: Int = 0

    private fun baseUrl() = "http://localhost:$port"

    private fun statusOf(block: () -> Unit): HttpStatus = try {
        block()
        HttpStatus.OK
    } catch (e: HttpClientErrorException) {
        HttpStatus.valueOf(e.statusCode.value())
    }

    @Test
    fun `api requests without a token get 401`() {
        val status = statusOf {
            RestTemplate().getForEntity("${baseUrl()}/api/v1/categories", List::class.java)
        }
        assertThat(status).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `authenticated but unapproved users get 403 on data endpoints`() {
        val status = statusOf {
            TestAuth.rest("newcomer~unapproved").getForEntity("${baseUrl()}/api/v1/categories", List::class.java)
        }
        assertThat(status).isEqualTo(HttpStatus.FORBIDDEN)
    }

    @Test
    fun `approved users reach data endpoints`() {
        val status = statusOf {
            TestAuth.rest().getForEntity("${baseUrl()}/api/v1/categories", List::class.java)
        }
        assertThat(status).isEqualTo(HttpStatus.OK)
    }

    @Test
    fun `SPA shell is not gated by auth`() {
        // The index.html fallback is absent in backend-only test builds (frontend skipped), which
        // yields a 500 from the resource handler — the point here is only that non-api paths are
        // never rejected by the security layer itself.
        val status = try {
            RestTemplate().getForEntity("${baseUrl()}/operations", String::class.java)
            HttpStatus.OK
        } catch (e: org.springframework.web.client.RestClientResponseException) {
            HttpStatus.valueOf(e.statusCode.value())
        }
        assertThat(status !in setOf(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)).isEqualTo(true)
    }
}
