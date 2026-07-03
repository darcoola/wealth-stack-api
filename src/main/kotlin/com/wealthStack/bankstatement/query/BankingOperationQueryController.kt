package com.wealthStack.bankstatement.query

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/bank-statements")
class BankingOperationQueryController(val finder: BankingOperationFinder) {

    @GetMapping
    fun getAll(
        @RequestParam(required = false) globalFilter: String?,
        @RequestParam(required = false, defaultValue = "false") needsVerificationOnly: Boolean,
        @RequestParam(required = false, defaultValue = "false") uncategorizedOnly: Boolean,
        @RequestParam(required = false) monthDate: String?,
        pageable: Pageable
    ): Page<OperationDto> = finder.findAll(globalFilter, needsVerificationOnly, uncategorizedOnly, monthDate, pageable)
}
