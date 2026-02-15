package com.wealthStack.bankstatement

import com.wealthStack.bankstatement.parser.StatementParserFactory
import com.wealthStack.bankstatement.query.toDto
import org.springframework.transaction.annotation.Transactional

open class StatementImporter(
    private val parserFactory: StatementParserFactory,
    private val repository: BankingOperationRepository,
    private val accountMappingRepository: AccountMappingRepository
) {

    @Transactional
    open fun importStatement(bankName: String, fileName: String, content: String): ImportResult {
        val parser = parserFactory.getParser(bankName)
        val operations = parser.parse(content, fileName)

        val mappings = accountMappingRepository.findAll().associate { it.rawAccount to it.displayName }
        operations.forEach { op ->
            mappings[op.account]?.let { op.displayName = it }
        }

        repository.saveAll(operations)

        return ImportResult(
            message = "Imported ${operations.size} operations from $fileName",
            bankName = bankName,
            fileName = fileName,
            operationsImported = operations.size,
            operations = operations.map { it.toDto() }
        )
    }
}
