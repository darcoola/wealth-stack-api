package com.wealthStack.bankstatement

import com.wealthStack.bankstatement.parser.ManualCsvParser
import com.wealthStack.bankstatement.parser.MBankCsvParser
import com.wealthStack.bankstatement.parser.PkoBpCsvParser
import com.wealthStack.bankstatement.parser.StatementParser
import com.wealthStack.bankstatement.parser.StatementParserFactory
import com.wealthStack.bankstatement.query.AccountMappingFinder
import com.wealthStack.bankstatement.query.AccountMappingQueryController
import com.wealthStack.bankstatement.query.BankingOperationFinder
import com.wealthStack.bankstatement.query.BankingOperationQueryController
import com.wealthStack.bankstatement.query.CategoryFinder
import com.wealthStack.bankstatement.query.CategoryQueryController
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class BankStatementConfig {

    @Bean
    fun mBankCsvParser(): MBankCsvParser = MBankCsvParser()

    @Bean
    fun pkoBpCsvParser(): PkoBpCsvParser = PkoBpCsvParser()

    @Bean
    fun manualCsvParser(): ManualCsvParser = ManualCsvParser()

    @Bean
    fun statementParserFactory(parsers: List<StatementParser>): StatementParserFactory =
        StatementParserFactory(parsers)

    @Bean
    fun statementImporter(
        parserFactory: StatementParserFactory,
        repository: BankingOperationRepository,
        accountMappingRepository: AccountMappingRepository,
        categoryRepository: CategoryRepository
    ): StatementImporter = StatementImporter(parserFactory, repository, accountMappingRepository, categoryRepository)

    @Bean
    fun accountMapper(
        accountMappingRepository: AccountMappingRepository,
        bankingOperationRepository: BankingOperationRepository
    ): AccountMapper = AccountMapper(accountMappingRepository, bankingOperationRepository)

    @Bean
    fun categoryService(
        categoryRepository: CategoryRepository,
        bankingOperationRepository: BankingOperationRepository
    ): CategoryService = CategoryService(categoryRepository, bankingOperationRepository)

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
    fun bankStatementController(importer: StatementImporter): BankStatementController =
        BankStatementController(importer)

    @Bean
    fun accountMappingController(mapper: AccountMapper): AccountMappingController =
        AccountMappingController(mapper)

    @Bean
    fun categoryController(service: CategoryService): CategoryController =
        CategoryController(service)

    @Bean
    fun operationCommandController(service: CategoryService): OperationCommandController =
        OperationCommandController(service)

    @Bean
    fun bankingOperationQueryController(finder: BankingOperationFinder): BankingOperationQueryController =
        BankingOperationQueryController(finder)

    @Bean
    fun accountMappingQueryController(finder: AccountMappingFinder): AccountMappingQueryController =
        AccountMappingQueryController(finder)

    @Bean
    fun categoryQueryController(finder: CategoryFinder): CategoryQueryController =
        CategoryQueryController(finder)
}
