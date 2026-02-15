package com.wealthStack.bankstatement.parser

import com.wealthStack.bankstatement.BankingOperation

interface StatementParser {
    val bankName: String
    fun parse(content: String, sourceFileName: String): List<BankingOperation>
}
