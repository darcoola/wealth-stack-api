package com.wealthStack.bankstatement

import org.springframework.transaction.annotation.Transactional

/**
 * Command side of the account-mapping dictionary: create, update, and delete entries that map a
 * per-party-unique raw account/card identifier to a friendly display name. Each change back-fills
 * the denormalized [BankingOperation.accountDisplayName] on every operation of that party with the
 * raw account, so the operations list always reflects the current mapping (deleting a mapping
 * reverts those operations to showing their raw account again). Another party's mappings are
 * treated as nonexistent ("not found").
 */
open class AccountMapper(
    private val accountMappingRepository: AccountMappingRepository,
    private val bankingOperationRepository: BankingOperationRepository
) {

    @Transactional
    open fun create(partyId: Long, rawAccount: String, displayName: String): AccountMapping {
        val account = rawAccount.trim()
        val name = displayName.trim()
        require(account.isNotEmpty()) { "Raw account must not be blank" }
        require(name.isNotEmpty()) { "Display name must not be blank" }
        require(accountMappingRepository.findByPartyIdAndRawAccount(partyId, account) == null) {
            "A mapping for account '$account' already exists"
        }

        val saved = accountMappingRepository.save(AccountMapping(account, name, partyId))
        applyToOperations(partyId, account, name)
        return saved
    }

    @Transactional
    open fun createAll(partyId: Long, mappings: List<Pair<String, String>>): List<AccountMapping> {
        return mappings.map { (rawAccount, displayName) -> create(partyId, rawAccount, displayName) }
    }

    @Transactional
    open fun update(partyId: Long, id: Long, rawAccount: String, displayName: String): AccountMapping {
        val account = rawAccount.trim()
        val name = displayName.trim()
        require(account.isNotEmpty()) { "Raw account must not be blank" }
        require(name.isNotEmpty()) { "Display name must not be blank" }

        val mapping = findOwned(partyId, id)
        val clash = accountMappingRepository.findByPartyIdAndRawAccount(partyId, account)
        require(clash == null || clash.id == id) { "A mapping for account '$account' already exists" }

        val previousAccount = mapping.rawAccount
        mapping.rawAccount = account
        mapping.displayName = name
        val saved = accountMappingRepository.save(mapping)

        if (previousAccount != account) clearFromOperations(partyId, previousAccount)
        applyToOperations(partyId, account, name)
        return saved
    }

    @Transactional
    open fun delete(partyId: Long, id: Long) {
        val mapping = findOwned(partyId, id)
        clearFromOperations(partyId, mapping.rawAccount)
        accountMappingRepository.delete(mapping)
    }

    private fun findOwned(partyId: Long, id: Long): AccountMapping =
        accountMappingRepository.findById(id)
            .filter { it.partyId == partyId }
            .orElseThrow { IllegalArgumentException("Account mapping $id not found") }

    private fun applyToOperations(partyId: Long, rawAccount: String, displayName: String) {
        val operations = bankingOperationRepository.findAllByPartyIdAndAccount(partyId, rawAccount)
        operations.forEach { it.accountDisplayName = displayName }
        bankingOperationRepository.saveAll(operations)
    }

    private fun clearFromOperations(partyId: Long, rawAccount: String) {
        val operations = bankingOperationRepository.findAllByPartyIdAndAccount(partyId, rawAccount)
        operations.forEach { it.accountDisplayName = null }
        bankingOperationRepository.saveAll(operations)
    }
}
