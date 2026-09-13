package com.wealthStack.bankstatement

import java.math.BigDecimal
import java.time.LocalDate

/**
 * A complete, portable snapshot of the user's data — the file behind the Administration page's
 * Export / Import. Entries reference each other by their unique natural keys (group and category
 * names, raw account identifiers), never by database ids, so a backup restores into any
 * installation, empty or not. See [DataBackupService].
 *
 * Optional fields are nullable rather than defaulted: Jackson maps an absent field to `null`, and a
 * non-null parameter would reject a hand-edited file that leaves it out.
 */
data class DataBackup(
    /** Always [FORMAT]; lets an import reject an unrelated JSON file early. */
    val format: String?,
    /** Schema version of this file; bumped on incompatible changes. */
    val version: Int?,
    val exportedAt: String? = null,
    val categoryGroups: List<BackupCategoryGroup>? = null,
    val categories: List<BackupCategory>? = null,
    val accountMappings: List<BackupAccountMapping>? = null,
    val operations: List<BackupOperation>? = null
) {
    companion object {
        const val FORMAT = "wealthstack-backup"
        const val VERSION = 1
    }
}

data class BackupCategoryGroup(val name: String)

data class BackupCategory(
    val name: String,
    /** Names a category group (from the file or already present); null = Ungrouped. */
    val group: String? = null
)

data class BackupAccountMapping(val rawAccount: String, val displayName: String)

data class BackupOperation(
    val date: LocalDate,
    val description: String,
    /** Signed; the CREDIT/DEBIT type is derived from the sign, as on every other ingest path. */
    val amount: BigDecimal,
    val bankName: String,
    val account: String,
    /** Names a category (from the file or already present); null = Uncategorized. */
    val category: String? = null,
    val additionalInfo: String? = null,
    val sourceFileName: String? = null,
    val needsVerification: Boolean? = null,
    /** Disambiguates genuinely identical operations (see [BankingOperation.occurrence]); null = 0. */
    val occurrence: Int? = null
)

/** Outcome of [DataBackupService.importBackup]. */
data class DataImportResult(
    /** Whether all existing data was wiped before the backup was loaded. */
    val replaced: Boolean,
    val categoryGroupsCreated: Int,
    val categoriesCreated: Int,
    val accountMappingsCreated: Int,
    /** Newly inserted operations. */
    val operationsImported: Int,
    /** Existing operations updated because the backup carried the same operation. */
    val operationsOverwritten: Int
)

/** Outcome of [DataBackupService.clearAll]: how many rows of each kind were deleted. */
data class DataClearResult(
    val operationsDeleted: Long,
    val categoriesDeleted: Long,
    val categoryGroupsDeleted: Long,
    val accountMappingsDeleted: Long
)
