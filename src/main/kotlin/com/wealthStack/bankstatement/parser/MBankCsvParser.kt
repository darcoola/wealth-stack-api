package com.wealthStack.bankstatement.parser

import com.wealthStack.bankstatement.BankingOperation
import com.wealthStack.bankstatement.OperationType
import java.math.BigDecimal
import java.time.LocalDate

class MBankCsvParser : StatementParser {

    override val bankName: String = "mbank"

    override fun parse(content: String, sourceFileName: String): List<BankingOperation> {
        val lines = content.lines()
        val dataStartIndex = lines.indexOfFirst { it.startsWith("#Data operacji;") }
        require(dataStartIndex >= 0) { "Could not find data header line in mBank CSV" }

        return lines.drop(dataStartIndex + 1)
            .filter { it.isNotBlank() }
            .map { parseLine(it, sourceFileName) }
    }

    private fun parseLine(line: String, sourceFileName: String): BankingOperation {
        val fields = line.split(";")
        require(fields.size >= 5) { "Invalid mBank CSV line: expected at least 5 fields" }

        val date = LocalDate.parse(fields[0].trim())
        val description = collapseWhitespace(unquote(fields[1]))
        val account = collapseWhitespace(unquote(fields[2]))
        val category = collapseWhitespace(unquote(fields[3]))
        val amount = parseAmount(fields[4])
        val type = if (amount >= BigDecimal.ZERO) OperationType.CREDIT else OperationType.DEBIT

        return BankingOperation(
            date = date,
            description = description,
            amount = amount,
            type = type,
            bankName = "mbank",
            account = account,
            category = category,
            sourceFileName = sourceFileName
        )
    }

    private fun unquote(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed.substring(1, trimmed.length - 1)
        } else {
            trimmed
        }
    }

    private fun collapseWhitespace(value: String): String =
        value.trim().replace(Regex("\\s+"), " ")

    private fun parseAmount(raw: String): BigDecimal {
        val cleaned = raw.trim()
            .replace(" PLN", "")
            .replace(" ", "")
            .replace(",", ".")
        return BigDecimal(cleaned)
    }
}
