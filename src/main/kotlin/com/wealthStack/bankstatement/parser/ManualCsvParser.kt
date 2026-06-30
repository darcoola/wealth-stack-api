package com.wealthStack.bankstatement.parser

import com.wealthStack.bankstatement.BankingOperation
import com.wealthStack.bankstatement.OperationType
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Parses already-prepared operation rows in WealthStack's own predefined CSV schema — used for
 * historical data or banks without a dedicated parser. Unlike the bank parsers this is the
 * canonical format we define, so each row carries its own [BankingOperation.bankName] column
 * (one file may mix banks); the upload `bankName=manual` only selects this parser.
 *
 * Format: UTF-8, comma-separated, quote-aware (`""` escapes a literal quote), with a header row
 * naming the columns (case-insensitive, order-independent):
 *
 * ```
 * date,bankName,account,description,amount,accountDisplayName,category
 * 2024-01-15,legacy,ACME 111,Salary,5000.00,Old Employer,Income
 * 2024-01-16,legacy,ACME 111,Groceries,-120.50,,
 * ```
 *
 * Required columns: `date` (ISO yyyy-MM-dd), `bankName`, `account`, `description`, `amount`
 * (dot decimal, optional minus). Optional: `accountDisplayName`, `category`. `type` is derived from the
 * amount sign. A non-blank `category` must name an existing dictionary entry (resolved at import
 * by [com.wealthStack.bankstatement.StatementImporter]; an unknown name fails the import); blank
 * or absent leaves the row Uncategorized.
 */
class ManualCsvParser : StatementParser {

    override val bankName: String = "manual"

    override fun parse(content: String, sourceFileName: String): List<BankingOperation> {
        val lines = content.lines().filter { it.isNotBlank() }
        require(lines.isNotEmpty()) { "Manual CSV is empty" }

        val columnIndex = parseCsvLine(lines.first())
            .withIndex()
            .associate { (index, name) -> name.trim().lowercase() to index }
        REQUIRED_COLUMNS.forEach { column ->
            require(column in columnIndex) {
                "Manual CSV missing required column '$column'. Expected header: $EXPECTED_HEADER"
            }
        }

        return lines.drop(1).map { parseLine(it, columnIndex, sourceFileName) }
    }

    private fun parseLine(
        line: String,
        columnIndex: Map<String, Int>,
        sourceFileName: String
    ): BankingOperation {
        val fields = parseCsvLine(line)
        fun required(column: String): String {
            val value = fields.getOrNull(columnIndex.getValue(column))?.trim()
            require(!value.isNullOrEmpty()) { "Manual CSV row missing value for required column '$column': $line" }
            return value
        }
        fun optional(column: String): String? =
            columnIndex[column]?.let { fields.getOrNull(it)?.trim() }?.takeIf { it.isNotEmpty() }

        val amount = parseAmount(required("amount"))
        return BankingOperation(
            date = LocalDate.parse(required("date")),
            description = required("description"),
            amount = amount,
            type = if (amount >= BigDecimal.ZERO) OperationType.CREDIT else OperationType.DEBIT,
            bankName = required("bankname"),
            account = required("account"),
            accountDisplayName = optional("accountdisplayname"),
            sourceFileName = sourceFileName
        ).apply { categoryName = optional("category") }
    }

    /** Splits a single CSV line on commas, respecting double-quoted fields and `""` escapes. */
    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    fields.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        fields.add(current.toString())
        return fields
    }

    private fun parseAmount(raw: String): BigDecimal = BigDecimal(raw.replace(" ", ""))

    private companion object {
        val REQUIRED_COLUMNS = listOf("date", "bankname", "account", "description", "amount")
        const val EXPECTED_HEADER = "date,bankName,account,description,amount,accountDisplayName,category"
    }
}
