package com.wealthStack.bankstatement.parser

import com.wealthStack.bankstatement.BankingOperation
import java.nio.charset.Charset

interface StatementParser {
    val bankName: String

    /** Character set the bank exports its statements in. */
    val charset: Charset
        get() = Charsets.UTF_8

    fun parse(content: String, sourceFileName: String): List<BankingOperation>
}
