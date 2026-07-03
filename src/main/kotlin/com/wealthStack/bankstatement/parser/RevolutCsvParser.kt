package com.wealthStack.bankstatement.parser

import com.wealthStack.bankstatement.BankingOperation
import com.wealthStack.bankstatement.OperationType
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Parses Revolut account statement CSV exports (Polish locale).
 *
 * The file is UTF-8, comma-separated, with fields wrapped in double quotes only when they contain
 * a comma, so a quote-aware splitter is required. Column layout:
 *
 * `Rodzaj, Produkt, Data rozpoczęcia, Data zrealizowania, Opis, Kwota, Opłata, Waluta, State, Saldo`
 *
 * - `Produkt` names the pocket the transaction belongs to (`Bieżące` = current, `Oszczędności` =
 *   savings). Revolut exports carry no account/IBAN, so this is used as the [BankingOperation.account]
 *   identifier — it keeps the two pockets apart for mappings and duplicate detection.
 * - `Data rozpoczęcia` (started) is a `yyyy-MM-dd HH:mm:ss` timestamp always present; its date part
 *   is used (`Data zrealizowania` can be blank for unsettled rows).
 * - `Kwota` is a dot-decimal signed amount (negative = outflow); the sign drives the type.
 * - Only completed rows (`State == ZAKOŃCZONO`) are imported — reverted/pending entries (e.g.
 *   `COFNIĘTO`) never hit the balance and are skipped. `Opłata` (fee) and `Saldo` (balance) are ignored.
 */
class RevolutCsvParser : StatementParser {

    override val bankName: String = "revolut"

    override fun canParse(content: String): Boolean =
        content.lineSequence().any { it.startsWith("Rodzaj,Produkt,") }

    override fun parse(content: String, sourceFileName: String): List<BankingOperation> {
        val lines = content.lines()
        val headerIndex = lines.indexOfFirst { it.startsWith("Rodzaj,Produkt,") }
        require(headerIndex >= 0) { "Could not find data header line in Revolut CSV" }

        return lines.drop(headerIndex + 1)
            .filter { it.isNotBlank() }
            .map { parseCsvLine(it) }
            .filter { it.getOrNull(STATE)?.trim() == COMPLETED_STATE }
            .map { toOperation(it, sourceFileName) }
    }

    private fun toOperation(fields: List<String>, sourceFileName: String): BankingOperation {
        require(fields.size > AMOUNT) { "Invalid Revolut CSV line: expected at least ${AMOUNT + 1} fields" }

        val date = LocalDate.parse(fields[STARTED_DATE].trim().substringBefore(" "))
        val amount = parseAmount(fields[AMOUNT])
        val type = if (amount >= BigDecimal.ZERO) OperationType.CREDIT else OperationType.DEBIT

        return BankingOperation(
            date = date,
            description = collapseWhitespace(fields[DESCRIPTION]),
            amount = amount,
            type = type,
            bankName = bankName,
            account = collapseWhitespace(fields[PRODUCT]),
            sourceFileName = sourceFileName
        )
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

    private fun collapseWhitespace(value: String): String =
        value.trim().replace(Regex("\\s+"), " ")

    private fun parseAmount(raw: String): BigDecimal =
        BigDecimal(raw.trim().replace(" ", ""))

    private companion object {
        const val PRODUCT = 1
        const val STARTED_DATE = 2
        const val DESCRIPTION = 4
        const val AMOUNT = 5
        const val STATE = 8
        const val COMPLETED_STATE = "ZAKOŃCZONO"
    }
}
