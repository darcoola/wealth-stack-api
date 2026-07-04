package com.wealthStack.party

import com.wealthStack.security.AuthConfigController
import com.wealthStack.security.PartyContextArgumentResolver
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/** Explicit bean wiring for the party/user domain, mirroring [com.wealthStack.bankstatement.BankStatementConfig]. */
@Configuration
class PartyConfig {

    @Bean
    fun userProvisioningService(
        appUserRepository: AppUserRepository,
        partyRepository: PartyRepository,
        membershipRepository: PartyMembershipRepository,
        transactionManager: PlatformTransactionManager,
        @Value("\${wealthstack.bootstrap.owner-email:}") bootstrapOwnerEmail: String,
    ): UserProvisioningService = UserProvisioningService(
        appUserRepository,
        partyRepository,
        membershipRepository,
        TransactionTemplate(transactionManager),
        bootstrapOwnerEmail,
    )

    @Bean
    fun partyContextArgumentResolver(
        provisioningService: UserProvisioningService,
        membershipRepository: PartyMembershipRepository,
    ): PartyContextArgumentResolver = PartyContextArgumentResolver(provisioningService, membershipRepository)

    @Bean
    fun authConfigController(
        @Value("\${wealthstack.auth.url}") url: String,
        @Value("\${wealthstack.auth.realm}") realm: String,
        @Value("\${wealthstack.auth.client-id}") clientId: String,
    ): AuthConfigController = AuthConfigController(url, realm, clientId)

    @Bean
    fun partyService(
        partyRepository: PartyRepository,
        appUserRepository: AppUserRepository,
        membershipRepository: PartyMembershipRepository,
    ): PartyService = PartyService(partyRepository, appUserRepository, membershipRepository)

    @Bean
    fun partyController(
        partyService: PartyService,
        membershipRepository: PartyMembershipRepository,
    ): PartyController = PartyController(partyService, membershipRepository)

    @Bean
    fun meController(
        provisioningService: UserProvisioningService,
        partyRepository: PartyRepository,
        membershipRepository: PartyMembershipRepository,
    ): MeController = MeController(provisioningService, partyRepository, membershipRepository)
}
