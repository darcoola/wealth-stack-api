package com.wealthStack.bankstatement.query

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/reports")
class ReportQueryController(val finder: ReportFinder) {

    @GetMapping("/category-monthly-totals")
    fun categoryMonthlyTotals(): List<MonthlyCategoryTotalDto> = finder.categoryMonthlyTotals()
}
