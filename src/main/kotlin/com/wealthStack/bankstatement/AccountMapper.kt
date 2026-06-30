package com.wealthStack.bankstatement

import org.springframework.transaction.annotation.Transactional

/**
 * Command side of the account-mapping dictionary: create, update, and delete entries that map a
 * unique raw account/card identifier to a friendly display name. Each change back-fills the
 * denormalized [BankingOperation.accountDisplayName] on every operation with that raw account, so
 * the operations list always reflects the current mapping (deleting a mapping reverts those
 * operations to showing their raw account again).
 */
open class AccountMapper(
    private val accountMappingRepository: AccountMappingRepository,
    private val bankingOperationRepository: BankingOperationRepository
) {

    @Transactional
    open fun create(rawAccount: String, displayName: String): AccountMapping {
        val account = rawAccount.trim()
        val name = displayName.trim()
        require(account.isNotEmpty()) { "Raw account must not be blank" }
        require(name.isNotEmpty()) { "Display name must not be blank" }
        require(accountMappingRepository.findByRawAccount(account) == null) {
            "A mapping for account '$account' already exists"
        }

        val saved = accountMappingRepository.save(AccountMapping(account, name))
        applyToOperations(account, name)
        return saved
    }

    @Transactional
    open fun update(id: Long, rawAccount: String, displayName: String): AccountMapping {
        val account = rawAccount.trim()
        val name = displayName.trim()
        require(account.isNotEmpty()) { "Raw account must not be blank" }
        require(name.isNotEmpty()) { "Display name must not be blank" }

        val mapping = accountMappingRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Account mapping $id not found") }
        val clash = accountMappingRepository.findByRawAccount(account)
        require(clash == null || clash.id == id) { "A mapping for account '$account' already exists" }

        val previousAccount = mapping.rawAccount
        mapping.rawAccount = account
        mapping.displayName = name
        val saved = accountMappingRepository.save(mapping)

        if (previousAccount != account) clearFromOperations(previousAccount)
        applyToOperations(account, name)
        return saved
    }

    @Transactional
    open fun delete(id: Long) {
        val mapping = accountMappingRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Account mapping $id not found") }
        clearFromOperations(mapping.rawAccount)
        accountMappingRepository.delete(mapping)
    }

    private fun applyToOperations(rawAccount: String, displayName: String) {
        val operations = bankingOperationRepository.findAllByAccount(rawAccount)
        operations.forEach { it.accountDisplayName = displayName }
        bankingOperationRepository.saveAll(operations)
    }

    private fun clearFromOperations(rawAccount: String) {
        val operations = bankingOperationRepository.findAllByAccount(rawAccount)
        operations.forEach { it.accountDisplayName = null }
        bankingOperationRepository.saveAll(operations)
    }
}
