package com.wealthStack.bankstatement.parser

class StatementParserFactory(parsers: List<StatementParser>) {

    private val parsersByBank: Map<String, StatementParser> =
        parsers.associateBy { it.bankName.lowercase() }

    fun getParser(bankName: String): StatementParser =
        parsersByBank[bankName.lowercase()]
            ?: throw IllegalArgumentException("Unsupported bank: $bankName. Supported: ${parsersByBank.keys}")

    /**
     * Picks the parser that recognizes the file [content] when the upload didn't name a bank. The
     * bytes are decoded leniently (ISO-8859-1 never fails and preserves the ASCII header markers the
     * parsers key on) so detection works before we know the real charset. Requires exactly one
     * match: no match or an ambiguous multi-match throws [IllegalArgumentException] (HTTP 400) so the
     * caller falls back to naming the bank explicitly.
     */
    fun detectParser(content: ByteArray): StatementParser {
        val text = String(content, Charsets.ISO_8859_1)
        val matches = parsersByBank.values.filter { it.canParse(text) }
        return when (matches.size) {
            1 -> matches.single()
            0 -> throw IllegalArgumentException(
                "Could not detect the statement format from the file content; specify bankName explicitly. Supported: ${parsersByBank.keys}"
            )
            else -> throw IllegalArgumentException(
                "Ambiguous statement format: content matches multiple parsers ${matches.map { it.bankName }}; specify bankName explicitly."
            )
        }
    }
}
