package com.wealthStack.bankstatement.query

import com.wealthStack.security.PartyContext
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/bank-statements")
class BankingOperationQueryController(val finder: BankingOperationFinder) {

    @GetMapping
    fun getAll(
        ctx: PartyContext,
        @RequestParam(required = false) globalFilter: String?,
        @RequestParam(required = false, defaultValue = "false") needsVerificationOnly: Boolean,
        @RequestParam(required = false, defaultValue = "false") uncategorizedOnly: Boolean,
        @RequestParam(required = false) accounts: List<String>?,
        @RequestParam(required = false, defaultValue = "false") unmappedAccount: Boolean,
        @RequestParam(required = false) groupIds: List<Long>?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateFrom: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateTo: LocalDate?,
        pageable: Pageable
    ): Page<OperationDto> = finder.findAll(
        ctx.partyId, globalFilter, needsVerificationOnly, uncategorizedOnly,
        accounts, unmappedAccount, groupIds, dateFrom, dateTo, pageable
    )
}
