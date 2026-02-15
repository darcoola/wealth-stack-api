package com.wealthStack.bankstatement

import org.springframework.transaction.annotation.Transactional

open class AccountMapper(
    private val accountMappingRepository: AccountMappingRepository,
    private val bankingOperationRepository: BankingOperationRepository
) {

    @Transactional
    open fun upsert(rawAccount: String, displayName: String): AccountMapping {
        val existing = accountMappingRepository.findByRawAccount(rawAccount)

        val saved = if (existing != null) {
            existing.displayName = displayName
            accountMappingRepository.save(existing)
        } else {
            accountMappingRepository.save(AccountMapping(rawAccount, displayName))
        }

        val operations = bankingOperationRepository.findAllByAccount(rawAccount)
        operations.forEach { it.displayName = displayName }
        bankingOperationRepository.saveAll(operations)

        return saved
    }
}
