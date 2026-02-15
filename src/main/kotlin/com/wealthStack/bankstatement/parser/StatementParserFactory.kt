package com.wealthStack.bankstatement.parser

class StatementParserFactory(parsers: List<StatementParser>) {

    private val parsersByBank: Map<String, StatementParser> =
        parsers.associateBy { it.bankName.lowercase() }

    fun getParser(bankName: String): StatementParser =
        parsersByBank[bankName.lowercase()]
            ?: throw IllegalArgumentException("Unsupported bank: $bankName. Supported: ${parsersByBank.keys}")
}
