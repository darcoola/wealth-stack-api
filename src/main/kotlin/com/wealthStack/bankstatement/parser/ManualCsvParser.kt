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
 * date,bankName,account,description,amount,accountDisplayName,additionalInfo,category
 * 2024-01-15,legacy,ACME 111,Salary,5000.00,Old Employer,,Income
 * 2024-01-16,legacy,ACME 111,Allegro,-120.50,,new phone case,
 * ```
 *
 * Required columns: `date` (ISO yyyy-MM-dd), `bankName`, `account`, `description`, `amount`
 * (dot or comma decimal, optional minus, optional space/NBSP thousands separators — e.g. `5000.00`
 * or `"-101 933,26"`). Optional: `accountDisplayName`, `additionalInfo` (free-text note when the
 * description alone — often just a shop name — isn't enough to deduce a category), `category`.
 * `type` is derived from the
 * amount sign. A non-blank `category` must name an existing dictionary entry (resolved at import
 * by [com.wealthStack.bankstatement.StatementImporter]; an unknown name fails the import); blank
 * or absent leaves the row Uncategorized.
 */
class ManualCsvParser : StatementParser {

    override val bankName: String = "manual"

    override fun canParse(content: String): Boolean {
        val header = parseCsvRecords(content).firstOrNull() ?: return false
        val columns = header.map { it.trim().lowercase() }.toSet()
        return REQUIRED_COLUMNS.all { it in columns }
    }

    override fun parse(content: String, sourceFileName: String): List<BankingOperation> {
        val records = parseCsvRecords(content)
        require(records.isNotEmpty()) { "Manual CSV is empty" }

        val columnIndex = records.first()
            .withIndex()
            .associate { (index, name) -> name.trim().lowercase() to index }
        REQUIRED_COLUMNS.forEach { column ->
            require(column in columnIndex) {
                "Manual CSV missing required column '$column'. Expected header: $EXPECTED_HEADER"
            }
        }

        return records.drop(1).map { parseRecord(it, columnIndex, sourceFileName) }
    }

    private fun parseRecord(
        fields: List<String>,
        columnIndex: Map<String, Int>,
        sourceFileName: String
    ): BankingOperation {
        fun required(column: String): String {
            val value = fields.getOrNull(columnIndex.getValue(column))?.trim()
            require(!value.isNullOrEmpty()) {
                "Manual CSV row missing value for required column '$column': ${fields.joinToString(",")}"
            }
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
            additionalInfo = optional("additionalinfo"),
            sourceFileName = sourceFileName
        ).apply { categoryName = optional("category") }
    }

    /**
     * Tokenizes the whole file into records of fields, respecting double-quoted fields and `""`
     * escapes. A record ends on a newline (`\n` or `\r\n`) only when not inside quotes, so a
     * quoted field may span multiple physical lines (e.g. a description with an embedded newline).
     * Blank records (from empty lines) are dropped.
     */
    private fun parseCsvRecords(content: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        fun endField() {
            fields.add(current.toString())
            current.clear()
        }
        fun endRecord() {
            endField()
            if (fields.size > 1 || fields.first().isNotBlank()) records.add(fields.toList())
            fields.clear()
        }

        var i = 0
        while (i < content.length) {
            val c = content[i]
            when {
                c == '"' && inQuotes && i + 1 < content.length && content[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> endField()
                (c == '\n' || c == '\r') && !inQuotes -> {
                    endRecord()
                    if (c == '\r' && i + 1 < content.length && content[i + 1] == '\n') i++
                }
                else -> current.append(c)
            }
            i++
        }
        if (current.isNotEmpty() || fields.isNotEmpty()) endRecord()
        return records
    }

    /**
     * Accepts both the canonical dot-decimal form (`5000.00`) and the European form produced by
     * Polish-locale spreadsheets — comma decimal with space or non-breaking-space thousands
     * separators (`"-101 933,26"`). Spaces (regular and NBSP) are stripped as grouping separators
     * and a decimal comma is normalized to a dot.
     */
    private fun parseAmount(raw: String): BigDecimal =
        BigDecimal(raw.replace("\u00A0", "").replace(" ", "").replace(",", "."))

    private companion object {
        val REQUIRED_COLUMNS = listOf("date", "bankname", "account", "description", "amount")
        const val EXPECTED_HEADER =
            "date,bankName,account,description,amount,accountDisplayName,additionalInfo,category"
    }
}
