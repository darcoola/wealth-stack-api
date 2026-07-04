package com.wealthStack.party

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.wealthStack.TestAuth
import com.wealthStack.bankstatement.search.AutoCategorizationService
import com.wealthStack.bankstatement.search.BankingOperationSearchRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * Bootstrap adoption is exercised with the sub `owner` because the test yml sets
 * `wealthstack.bootstrap.owner-email: owner@test.local` (TestAuth derives email from sub).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestAuth::class)
class MeAndPartyContextTest {
    @MockitoBean
    lateinit var searchRepository: BankingOperationSearchRepository

    @MockitoBean
    lateinit var elasticsearchOperations: ElasticsearchOperations

    @MockitoBean
    lateinit var autoCategorizationService: AutoCategorizationService

    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var appUserRepository: AppUserRepository

    @Autowired
    lateinit var membershipRepository: PartyMembershipRepository

    @Autowired
    lateinit var partyRepository: PartyRepository

    @BeforeEach
    fun clean() {
        // Memberships/users reset so each test provisions from scratch (and party 1 is adoptable
        // again). Parties themselves are left in place: the H2 database is shared with other test
        // classes in the same cached context, whose data rows hold FKs to their parties.
        membershipRepository.deleteAll()
        appUserRepository.deleteAll()
    }

    private fun baseUrl() = "http://localhost:$port"

    private fun me(token: String): Map<*, *> =
        TestAuth.rest(token).getForEntity("${baseUrl()}/api/v1/me", Map::class.java).body!!

    @Test
    fun `first call provisions user with a fresh personal party`() {
        val body = me("alice")

        assertThat(body["approved"]).isEqualTo(true)
        assertThat(body["email"]).isEqualTo("alice@test.local")
        val user = appUserRepository.findByKeycloakSub("alice")
        assertThat(user).isNotNull()
        // Not the bootstrap party — alice's email doesn't match the configured owner email.
        assertThat(user!!.personalPartyId == UserProvisioningService.BOOTSTRAP_PARTY_ID).isEqualTo(false)
        val membership = membershipRepository.findByPartyIdAndUserId(user.personalPartyId, user.id!!)
        assertThat(membership!!.role).isEqualTo(PartyRole.OWNER)
        val parties = body["parties"] as List<*>
        assertThat(parties.size).isEqualTo(1)
    }

    @Test
    fun `bootstrap owner adopts the well-known default party`() {
        val body = me("owner")

        assertThat((body["personalPartyId"] as Number).toLong())
            .isEqualTo(UserProvisioningService.BOOTSTRAP_PARTY_ID)
        val user = appUserRepository.findByKeycloakSub("owner")
        assertThat(user!!.personalPartyId).isEqualTo(UserProvisioningService.BOOTSTRAP_PARTY_ID)
    }

    @Test
    fun `bootstrap party is adopted only once`() {
        me("owner")
        appUserRepository.findByKeycloakSub("owner")!!.let { owner ->
            membershipRepository.deleteAll(membershipRepository.findAllByUserId(owner.id!!))
            appUserRepository.delete(owner)
        }
        // Party 1 still has no members now, but a *different* email must never adopt it.
        val body = me("somebody-else")
        assertThat((body["personalPartyId"] as Number).toLong() == UserProvisioningService.BOOTSTRAP_PARTY_ID)
            .isEqualTo(false)
    }

    @Test
    fun `unapproved user gets approved=false and no provisioning`() {
        val body = me("pending~unapproved")

        assertThat(body["approved"]).isEqualTo(false)
        assertThat(body["personalPartyId"]).isNull()
        assertThat(appUserRepository.findByKeycloakSub("pending")).isNull()
    }

}
