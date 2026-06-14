package com.wealthStack.bankstatement.parser

import com.wealthStack.bankstatement.BankingOperation
import com.wealthStack.bankstatement.OperationType
import java.math.BigDecimal
import java.nio.charset.Charset
import java.time.LocalDate

/**
 * Parses PKO BP transaction history exports.
 *
 * The file is comma-separated with every field wrapped in double quotes, encoded in
 * Windows-1250. Quoted fields may themselves contain commas (e.g. Polish decimal amounts
 * inside a description), so a quote-aware splitter is required.
 *
 * Column layout:
 * `Data operacji, Data waluty, Typ transakcji, Kwota, Waluta, Saldo po transakcji, Opis transakcji...`
 *
 * The description spans several trailing columns ("Tytuł", "Lokalizacja", "Numer karty",
 * "Rachunek nadawcy", ...) whose presence depends on the transaction type.
 */
class PkoBpCsvParser : StatementParser {

    override val bankName: String = "pkobp"

    override val charset: Charset = Charset.forName("windows-1250")

    override fun parse(content: String, sourceFileName: String): List<BankingOperation> {
        val lines = content.lines()
        val headerIndex = lines.indexOfFirst { parseCsvLine(it).firstOrNull()?.trim() == "Data operacji" }
        require(headerIndex >= 0) { "Could not find data header line in PKO BP CSV" }

        return lines.drop(headerIndex + 1)
            .filter { it.isNotBlank() }
            .map { parseLine(it, sourceFileName) }
    }

    private fun parseLine(line: String, sourceFileName: String): BankingOperation {
        val fields = parseCsvLine(line)
        require(fields.size >= 6) { "Invalid PKO BP CSV line: expected at least 6 fields" }

        val date = LocalDate.parse(fields[0].trim())
        val transactionType = collapseWhitespace(fields[2])
        val amount = parseAmount(fields[3])
        val type = if (amount >= BigDecimal.ZERO) OperationType.CREDIT else OperationType.DEBIT

        val descriptionFields = fields.drop(6).map { collapseWhitespace(it) }.filter { it.isNotEmpty() }
        val description = descriptionFields.joinToString(" | ")
        val account = extractAccount(descriptionFields)

        return BankingOperation(
            date = date,
            description = description,
            amount = amount,
            type = type,
            bankName = bankName,
            account = account,
            category = transactionType,
            sourceFileName = sourceFileName
        )
    }

    /**
     * Picks a stable account identifier from the description fields: the card number for card
     * operations, otherwise the counterparty account for transfers, otherwise empty.
     */
    private fun extractAccount(descriptionFields: List<String>): String =
        findLabeledValue(descriptionFields, "Numer karty:")
            ?: findLabeledValue(descriptionFields, "Rachunek nadawcy:")
            ?: ""

    private fun findLabeledValue(fields: List<String>, label: String): String? =
        fields.firstOrNull { it.startsWith(label) }?.substringAfter(label)?.trim()?.takeIf { it.isNotEmpty() }

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

    private fun collapseWhitespace(value: String): String =
        value.trim().replace(Regex("\\s+"), " ")

    private fun parseAmount(raw: String): BigDecimal =
        BigDecimal(raw.trim().replace(" ", ""))
}
