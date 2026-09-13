package com.wealthStack.bankstatement

import com.wealthStack.bankstatement.search.AutoCategorizationService
import org.springframework.data.domain.Sort
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/**
 * Whole-dataset export and import ([DataBackup]) — how a user moves their data between
 * installations or keeps a backup.
 *
 * Import runs in a single transaction, so a file that fails validation (wrong format, an unknown
 * category or group, the same operation twice) changes nothing. Two modes:
 *  - **merge** (default): the backup is laid over the existing data. Groups, categories and account
 *    mappings are matched by name / raw account and operations by their duplicate-detection identity
 *    (fingerprint + occurrence), so re-importing the same file is a no-op. On a match the file wins:
 *    a category's group, a mapping's display name and an operation's category, note, provenance and
 *    verification flag are taken from the backup. Local data the file doesn't mention is kept.
 *  - **replace**: every operation, category, group and mapping is deleted first, so the result is
 *    exactly the backup's content.
 */
open class DataBackupService(
    private val categoryGroupRepository: CategoryGroupRepository,
    private val categoryRepository: CategoryRepository,
    private val accountMappingRepository: AccountMappingRepository,
    private val operationRepository: BankingOperationRepository,
    private val autoCategorizationService: AutoCategorizationService
) {

    @Transactional(readOnly = true)
    open fun export(): DataBackup = DataBackup(
        format = DataBackup.FORMAT,
        version = DataBackup.VERSION,
        exportedAt = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString(),
        categoryGroups = categoryGroupRepository.findAll(Sort.by("name")).map { BackupCategoryGroup(it.name) },
        categories = categoryRepository.findAll(Sort.by("name")).map { BackupCategory(it.name, it.group?.name) },
        accountMappings = accountMappingRepository.findAll(Sort.by("rawAccount"))
            .map { BackupAccountMapping(it.rawAccount, it.displayName) },
        operations = operationRepository.findAll(Sort.by("date", "id")).map {
            BackupOperation(
                date = it.date,
                description = it.description,
                amount = it.amount,
                bankName = it.bankName,
                account = it.account,
                category = it.category?.name,
                additionalInfo = it.additionalInfo,
                sourceFileName = it.sourceFileName,
                needsVerification = it.needsVerification,
                occurrence = it.occurrence
            )
        }
    )

    @Transactional
    open fun importBackup(backup: DataBackup, replace: Boolean): DataImportResult {
        require(backup.format == DataBackup.FORMAT) { "This file is not a WealthStack backup" }
        require(backup.version == DataBackup.VERSION) {
            "Unsupported backup version ${backup.version} (this installation reads version ${DataBackup.VERSION})"
        }

        if (replace) {
            operationRepository.deleteAllInBatch()
            categoryRepository.deleteAllInBatch()
            categoryGroupRepository.deleteAllInBatch()
            accountMappingRepository.deleteAllInBatch()
        }

        val groups = categoryGroupRepository.findAll().associateByTo(HashMap()) { it.name }
        var groupsCreated = 0
        backup.categoryGroups.orEmpty().forEach { entry ->
            require(entry.name.isNotBlank()) { "Category group names must not be blank" }
            if (entry.name !in groups) {
                groups[entry.name] = categoryGroupRepository.save(CategoryGroup(entry.name))
                groupsCreated++
            }
        }

        val categories = categoryRepository.findAll().associateByTo(HashMap()) { it.name }
        var categoriesCreated = 0
        backup.categories.orEmpty().forEach { entry ->
            require(entry.name.isNotBlank()) { "Category names must not be blank" }
            val group = entry.group?.let {
                groups[it] ?: throw IllegalArgumentException("Category '${entry.name}' refers to unknown group '$it'")
            }
            val existing = categories[entry.name]
            if (existing == null) {
                categories[entry.name] = categoryRepository.save(Category(entry.name, group))
                categoriesCreated++
            } else {
                existing.group = group
            }
        }

        val mappings = accountMappingRepository.findAll().associateByTo(HashMap()) { it.rawAccount }
        var mappingsCreated = 0
        backup.accountMappings.orEmpty().forEach { entry ->
            // No blank checks: a statement without an account number (e.g. a PKO BP row with no card or
            // sender account) is auto-mapped as "" → "", and such a backup must still restore.
            val existing = mappings[entry.rawAccount]
            if (existing == null) {
                mappings[entry.rawAccount] = accountMappingRepository.save(AccountMapping(entry.rawAccount, entry.displayName))
                mappingsCreated++
            } else if (existing.displayName != entry.displayName) {
                existing.displayName = entry.displayName
                // Keep the denormalized name in step on local operations the backup doesn't carry.
                operationRepository.findAllByAccount(entry.rawAccount).forEach { it.accountDisplayName = entry.displayName }
            }
        }

        val incoming = backup.operations.orEmpty().map { entry ->
            BankingOperation(
                date = entry.date,
                description = entry.description,
                amount = entry.amount,
                type = if (entry.amount >= BigDecimal.ZERO) OperationType.CREDIT else OperationType.DEBIT,
                bankName = entry.bankName,
                account = entry.account,
                additionalInfo = entry.additionalInfo,
                category = entry.category?.let {
                    categories[it] ?: throw IllegalArgumentException(
                        "Operation '${entry.description}' on ${entry.date} refers to unknown category '$it'"
                    )
                },
                sourceFileName = entry.sourceFileName,
                occurrence = entry.occurrence ?: 0,
                needsVerification = entry.needsVerification ?: false
            ).apply { fingerprint = OperationFingerprint.of(this) }
        }

        incoming.groupBy { it.fingerprint to it.occurrence }.values.firstOrNull { it.size > 1 }?.let {
            val op = it.first()
            throw IllegalArgumentException(
                "The backup contains the operation '${op.description}' on ${op.date} twice with the same occurrence"
            )
        }

        // Every operation is connected to a mapping, exactly as after a statement import.
        incoming.map { it.account }.distinct().filter { it !in mappings }.forEach { rawAccount ->
            mappings[rawAccount] = accountMappingRepository.save(AccountMapping(rawAccount, rawAccount))
            mappingsCreated++
        }
        incoming.forEach { it.accountDisplayName = mappings[it.account]?.displayName }

        val existingByIdentity = incoming.map { it.fingerprint }.distinct()
            .chunked(1000)
            .flatMap { operationRepository.findAllByFingerprintIn(it) }
            .associateBy { it.fingerprint to it.occurrence }

        var imported = 0
        var overwritten = 0
        val persisted = incoming.map { op ->
            val existing = existingByIdentity[op.fingerprint to op.occurrence]
            if (existing == null) {
                imported++
                op
            } else {
                overwritten++
                existing.accountDisplayName = op.accountDisplayName
                existing.category = op.category
                existing.additionalInfo = op.additionalInfo
                existing.sourceFileName = op.sourceFileName
                existing.needsVerification = op.needsVerification
                existing
            }
        }
        operationRepository.saveAll(persisted)

        // Re-train category suggestions on what was just loaded; a replace also drops what the
        // deleted data had taught the index.
        if (replace) autoCategorizationService.clearIndex()
        autoCategorizationService.indexOperations(persisted)

        return DataImportResult(
            replaced = replace,
            categoryGroupsCreated = groupsCreated,
            categoriesCreated = categoriesCreated,
            accountMappingsCreated = mappingsCreated,
            operationsImported = imported,
            operationsOverwritten = overwritten
        )
    }
}
