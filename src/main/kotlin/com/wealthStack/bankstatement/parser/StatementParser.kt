package com.wealthStack.bankstatement.parser

import com.wealthStack.bankstatement.BankingOperation
import java.nio.charset.Charset

interface StatementParser {
    val bankName: String

    /** Character set the bank exports its statements in. */
    val charset: Charset
        get() = Charsets.UTF_8

    /**
     * Whether this parser can parse [content] — used to auto-detect the target parser when the
     * upload omits `bankName`. [content] is decoded leniently (ISO-8859-1) so it may not be the
     * parser's real [charset]; the check must therefore rely only on ASCII header markers.
     */
    fun canParse(content: String): Boolean

    fun parse(content: String, sourceFileName: String): List<BankingOperation>
}
