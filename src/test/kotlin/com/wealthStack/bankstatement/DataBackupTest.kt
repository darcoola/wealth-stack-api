package com.wealthStack.bankstatement

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.startsWith
import com.wealthStack.bankstatement.search.AutoCategorizationService
import com.wealthStack.bankstatement.search.BankingOperationSearchRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import java.time.LocalDate

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class DataBackupTest {
    @MockitoBean
    lateinit var searchRepository: BankingOperationSearchRepository

    @MockitoBean
    lateinit var elasticsearchOperations: ElasticsearchOperations

    @MockitoBean
    lateinit var autoCategorizationService: AutoCategorizationService

    @LocalServerPort
    var port: Int = 0

    @Autowired lateinit var backupService: DataBackupService
    @Autowired lateinit var importer: StatementImporter
    @Autowired lateinit var categoryService: CategoryService
    @Autowired lateinit var categoryGroupService: CategoryGroupService
    @Autowired lateinit var operationRepository: BankingOperationRepository
    @Autowired lateinit var categoryRepository: CategoryRepository
    @Autowired lateinit var categoryGroupRepository: CategoryGroupRepository
    @Autowired lateinit var accountMappingRepository: AccountMappingRepository

    @BeforeEach
    fun clean() = wipe()

    private fun wipe() {
        operationRepository.deleteAll()
        categoryRepository.deleteAll()
        categoryGroupRepository.deleteAll()
        accountMappingRepository.deleteAll()
    }

    /** A small but representative dataset: grouped + ungrouped categories, a renamed mapping, notes,
     *  a pending prediction and two genuinely identical cash spends (occurrence 0 and 1). */
    private fun seed() {
        val spending = categoryGroupService.create("Spending")
        categoryGroupService.create("Unused group")
        categoryService.create("Groceries", spending.id)
        categoryService.create("Salary")
        importer.importOperations(
            ManualOperationsRequest(
                bankName = "legacy",
                source = "2024-history",
                operations = listOf(
                    ManualOperation(LocalDate.of(2024, 1, 15), "Salary", BigDecimal("5000.00"), "ACME 111", category = "Salary"),
                    ManualOperation(LocalDate.of(2024, 1, 16), "Market", BigDecimal("-120.50"), "ACME 111", additionalInfo = "weekly shop"),
                )
            )
        )
        accountMappingRepository.findByRawAccount("ACME 111")!!.let {
            it.displayName = "Main account"
            accountMappingRepository.save(it)
        }
        operationRepository.findAll().first { it.description == "Market" }.let {
            it.accountDisplayName = "Main account"
            it.needsVerification = true
            it.category = categoryRepository.findByName("Groceries")
            operationRepository.save(it)
        }
        val coffee = NewOperationRequest(LocalDate.of(2024, 1, 17), "Coffee", BigDecimal("-12.00"))
        importer.addCashOperation(coffee)
        importer.addCashOperation(coffee.copy(force = true))
    }

    private fun DataBackup.withoutTimestamp() = copy(exportedAt = null)

    @Test
    fun `a backup restored into an empty installation reproduces the data exactly`() {
        seed()
        val backup = backupService.export()
        wipe()

        val result = backupService.importBackup(backup, replace = false)

        assertThat(result.operationsImported).isEqualTo(4)
        assertThat(result.operationsOverwritten).isEqualTo(0)
        assertThat(result.categoryGroupsCreated).isEqualTo(2)
        assertThat(result.categoriesCreated).isEqualTo(2)
        assertThat(backupService.export().withoutTimestamp()).isEqualTo(backup.withoutTimestamp())

        val market = operationRepository.findAll().single { it.description == "Market" }
        assertThat(market.accountDisplayName).isEqualTo("Main account")
        assertThat(market.additionalInfo).isEqualTo("weekly shop")
        assertThat(market.needsVerification).isEqualTo(true)
        assertThat(market.category?.group?.name).isEqualTo("Spending")
    }

    @Test
    fun `re-importing the same backup changes nothing`() {
        seed()
        val backup = backupService.export()

        val result = backupService.importBackup(backup, replace = false)

        assertThat(result.operationsImported).isEqualTo(0)
        assertThat(result.operationsOverwritten).isEqualTo(4)
        assertThat(result.categoriesCreated).isEqualTo(0)
        assertThat(backupService.export().withoutTimestamp()).isEqualTo(backup.withoutTimestamp())
    }

    @Test
    fun `merge keeps local data and lets the backup win where both have an entry`() {
        seed()
        val backup = backupService.export()
        categoryService.create("Local only")
        importer.addCashOperation(NewOperationRequest(LocalDate.of(2024, 2, 1), "Local cake", BigDecimal("-8.00")))
        accountMappingRepository.findByRawAccount("ACME 111")!!.let {
            it.displayName = "Renamed locally"
            accountMappingRepository.save(it)
        }

        backupService.importBackup(backup, replace = false)

        assertThat(categoryRepository.findByName("Local only")).isNotNull()
        assertThat(operationRepository.findAll().size).isEqualTo(5)
        assertThat(accountMappingRepository.findByRawAccount("ACME 111")!!.displayName).isEqualTo("Main account")
    }

    @Test
    fun `replace leaves exactly the backup's content`() {
        seed()
        val backup = backupService.export()
        categoryService.create("Local only")
        importer.addCashOperation(NewOperationRequest(LocalDate.of(2024, 2, 1), "Local cake", BigDecimal("-8.00")))

        val result = backupService.importBackup(backup, replace = true)

        assertThat(result.replaced).isEqualTo(true)
        assertThat(result.operationsImported).isEqualTo(4)
        assertThat(categoryRepository.findByName("Local only")).isNull()
        assertThat(backupService.export().withoutTimestamp()).isEqualTo(backup.withoutTimestamp())
    }

    @Test
    fun `an invalid file is rejected without touching existing data`() {
        seed()
        val backup = backupService.export()

        assertThrows<IllegalArgumentException> {
            backupService.importBackup(backup.copy(format = "something-else"), replace = true)
        }
        val broken = backup.copy(
            categoryGroups = backup.categoryGroups!! + BackupCategoryGroup("Brand new group"),
            operations = backup.operations!! + backup.operations!!.first().copy(category = "Missing category")
        )
        assertThrows<IllegalArgumentException> { backupService.importBackup(broken, replace = true) }

        assertThat(categoryGroupRepository.findByName("Brand new group")).isNull()
        assertThat(backupService.export().withoutTimestamp()).isEqualTo(backup.withoutTimestamp())
    }

    @Test
    fun `an operation without an account number survives the round trip`() {
        // Parsers record a missing account as "", and the import auto-maps it as "" -> "".
        importer.importOperations(
            ManualOperationsRequest(
                bankName = "pkobp",
                operations = listOf(ManualOperation(LocalDate.of(2024, 3, 1), "Bank fee", BigDecimal("-5.00"), ""))
            )
        )
        val backup = backupService.export()
        assertThat(backup.accountMappings!!.single().rawAccount).isEqualTo("")

        assertThat(backupService.importBackup(backup, replace = false).operationsOverwritten).isEqualTo(1)
        backupService.importBackup(backup, replace = true)
        assertThat(backupService.export().withoutTimestamp()).isEqualTo(backup.withoutTimestamp())
    }

    @Test
    fun `the same operation twice in one file is rejected`() {
        seed()
        val backup = backupService.export()
        val duplicated = backup.copy(operations = backup.operations!! + backup.operations!!.first())

        val error = assertThrows<IllegalArgumentException> { backupService.importBackup(duplicated, replace = false) }
        assertThat(error.message!!).contains("twice")
    }

    @Test
    fun `export downloads an attachment that the import endpoint accepts`() {
        seed()
        val rest = RestTemplate()

        val export = rest.getForEntity("http://localhost:$port/api/v1/data/export", String::class.java)
        assertThat(export.headers.contentDisposition.type).isEqualTo("attachment")
        assertThat(export.headers.contentDisposition.filename!!).startsWith("wealthstack-backup-")
        assertThat(export.body!!).contains("\"format\":\"wealthstack-backup\"")

        wipe()
        val result = rest.postForEntity(
            "http://localhost:$port/api/v1/data/import?replace=true",
            HttpEntity(export.body, HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
            Map::class.java
        ).body!!

        assertThat(result["operationsImported"]).isEqualTo(4)
        assertThat(operationRepository.findAll().size).isEqualTo(4)
    }
}
