package com.wealthStack.bankstatement

import com.wealthStack.bankstatement.parser.ManualCsvParser
import com.wealthStack.bankstatement.parser.MBankCsvParser
import com.wealthStack.bankstatement.parser.PkoBpCsvParser
import com.wealthStack.bankstatement.parser.RevolutCsvParser
import com.wealthStack.bankstatement.parser.StatementParser
import com.wealthStack.bankstatement.parser.StatementParserFactory
import com.wealthStack.bankstatement.query.AccountMappingFinder
import com.wealthStack.bankstatement.query.AccountMappingQueryController
import com.wealthStack.bankstatement.query.BankingOperationFinder
import com.wealthStack.bankstatement.query.BankingOperationQueryController
import com.wealthStack.bankstatement.query.CategoryFinder
import com.wealthStack.bankstatement.query.CategoryGroupFinder
import com.wealthStack.bankstatement.query.CategoryGroupQueryController
import com.wealthStack.bankstatement.query.CategoryQueryController
import com.wealthStack.bankstatement.query.ReportFinder
import com.wealthStack.bankstatement.query.ReportQueryController
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import com.wealthStack.bankstatement.search.AutoCategorizationService
import com.wealthStack.bankstatement.search.BankingOperationSearchRepository
import org.springframework.data.elasticsearch.core.ElasticsearchOperations

@Configuration
class BankStatementConfig {

    @Bean
    fun mBankCsvParser(): MBankCsvParser = MBankCsvParser()

    @Bean
    fun pkoBpCsvParser(): PkoBpCsvParser = PkoBpCsvParser()

    @Bean
    fun revolutCsvParser(): RevolutCsvParser = RevolutCsvParser()

    @Bean
    fun manualCsvParser(): ManualCsvParser = ManualCsvParser()

    @Bean
    fun statementParserFactory(parsers: List<StatementParser>): StatementParserFactory =
        StatementParserFactory(parsers)

    @Bean
    fun autoCategorizationService(
        searchRepository: BankingOperationSearchRepository,
        elasticsearchOperations: ElasticsearchOperations,
        categoryRepository: CategoryRepository
    ): AutoCategorizationService {
        return AutoCategorizationService(searchRepository, elasticsearchOperations, categoryRepository)
    }

    @Bean
    fun statementImporter(
        parserFactory: StatementParserFactory,
        repository: BankingOperationRepository,
        accountMappingRepository: AccountMappingRepository,
        categoryRepository: CategoryRepository,
        autoCategorizationService: AutoCategorizationService
    ): StatementImporter = StatementImporter(parserFactory, repository, accountMappingRepository, categoryRepository, autoCategorizationService)

    @Bean
    fun accountMapper(
        accountMappingRepository: AccountMappingRepository,
        bankingOperationRepository: BankingOperationRepository
    ): AccountMapper = AccountMapper(accountMappingRepository, bankingOperationRepository)

    @Bean
    fun categoryService(
        categoryRepository: CategoryRepository,
        categoryGroupRepository: CategoryGroupRepository,
        bankingOperationRepository: BankingOperationRepository,
        autoCategorizationService: com.wealthStack.bankstatement.search.AutoCategorizationService
    ): CategoryService = CategoryService(categoryRepository, categoryGroupRepository, bankingOperationRepository, autoCategorizationService)

    @Bean
    fun categoryGroupService(
        categoryGroupRepository: CategoryGroupRepository,
        categoryRepository: CategoryRepository
    ): CategoryGroupService = CategoryGroupService(categoryGroupRepository, categoryRepository)

    @Bean
    fun bankingOperationFinder(
        repository: BankingOperationRepository
    ): BankingOperationFinder = BankingOperationFinder(repository)

    @Bean
    fun accountMappingFinder(
        repository: AccountMappingRepository
    ): AccountMappingFinder = AccountMappingFinder(repository)

    @Bean
    fun categoryFinder(
        repository: CategoryRepository
    ): CategoryFinder = CategoryFinder(repository)

    @Bean
    fun categoryGroupFinder(
        repository: CategoryGroupRepository
    ): CategoryGroupFinder = CategoryGroupFinder(repository)

    @Bean
    fun reportFinder(
        repository: BankingOperationRepository
    ): ReportFinder = ReportFinder(repository)

    @Bean
    fun bankStatementController(importer: StatementImporter): BankStatementController =
        BankStatementController(importer)

    @Bean
    fun accountMappingController(mapper: AccountMapper): AccountMappingController =
        AccountMappingController(mapper)

    @Bean
    fun categoryController(service: CategoryService): CategoryController =
        CategoryController(service)

    @Bean
    fun categoryGroupController(service: CategoryGroupService): CategoryGroupController =
        CategoryGroupController(service)

    @Bean
    fun operationCommandService(
        bankingOperationRepository: BankingOperationRepository
    ): OperationCommandService = OperationCommandService(bankingOperationRepository)

    @Bean
    fun operationCommandController(
        service: CategoryService,
        operationService: OperationCommandService
    ): OperationCommandController = OperationCommandController(service, operationService)

    @Bean
    fun bankingOperationQueryController(finder: BankingOperationFinder): BankingOperationQueryController =
        BankingOperationQueryController(finder)

    @Bean
    fun accountMappingQueryController(finder: AccountMappingFinder): AccountMappingQueryController =
        AccountMappingQueryController(finder)

    @Bean
    fun categoryQueryController(finder: CategoryFinder): CategoryQueryController =
        CategoryQueryController(finder)

    @Bean
    fun categoryGroupQueryController(finder: CategoryGroupFinder): CategoryGroupQueryController =
        CategoryGroupQueryController(finder)

    @Bean
    fun reportQueryController(finder: ReportFinder): ReportQueryController =
        ReportQueryController(finder)
}
