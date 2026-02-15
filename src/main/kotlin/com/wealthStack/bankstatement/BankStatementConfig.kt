package com.wealthStack.bankstatement

import com.wealthStack.bankstatement.parser.MBankCsvParser
import com.wealthStack.bankstatement.parser.StatementParser
import com.wealthStack.bankstatement.parser.StatementParserFactory
import com.wealthStack.bankstatement.query.AccountMappingFinder
import com.wealthStack.bankstatement.query.AccountMappingQueryController
import com.wealthStack.bankstatement.query.BankingOperationFinder
import com.wealthStack.bankstatement.query.BankingOperationQueryController
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class BankStatementConfig {

    @Bean
    fun mBankCsvParser(): MBankCsvParser = MBankCsvParser()

    @Bean
    fun statementParserFactory(parsers: List<StatementParser>): StatementParserFactory =
        StatementParserFactory(parsers)

    @Bean
    fun statementImporter(
        parserFactory: StatementParserFactory,
        repository: BankingOperationRepository,
        accountMappingRepository: AccountMappingRepository
    ): StatementImporter = StatementImporter(parserFactory, repository, accountMappingRepository)

    @Bean
    fun accountMapper(
        accountMappingRepository: AccountMappingRepository,
        bankingOperationRepository: BankingOperationRepository
    ): AccountMapper = AccountMapper(accountMappingRepository, bankingOperationRepository)

    @Bean
    fun bankingOperationFinder(
        repository: BankingOperationRepository
    ): BankingOperationFinder = BankingOperationFinder(repository)

    @Bean
    fun accountMappingFinder(
        repository: AccountMappingRepository
    ): AccountMappingFinder = AccountMappingFinder(repository)

    @Bean
    fun bankStatementController(importer: StatementImporter): BankStatementController =
        BankStatementController(importer)

    @Bean
    fun accountMappingController(mapper: AccountMapper): AccountMappingController =
        AccountMappingController(mapper)

    @Bean
    fun bankingOperationQueryController(finder: BankingOperationFinder): BankingOperationQueryController =
        BankingOperationQueryController(finder)

    @Bean
    fun accountMappingQueryController(finder: AccountMappingFinder): AccountMappingQueryController =
        AccountMappingQueryController(finder)
}
