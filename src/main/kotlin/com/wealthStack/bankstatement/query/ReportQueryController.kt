package com.wealthStack.bankstatement.query

import com.wealthStack.security.PartyContext
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/reports")
class ReportQueryController(val finder: ReportFinder) {

    @GetMapping("/category-monthly-totals")
    fun categoryMonthlyTotals(ctx: PartyContext): List<MonthlyCategoryTotalDto> =
        finder.categoryMonthlyTotals(ctx.partyId)
}
