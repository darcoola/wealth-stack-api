package com.wealthStack.bankstatement.query

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/bank-statements")
class BankingOperationQueryController(val finder: BankingOperationFinder) {

    @GetMapping
    fun getAll(): List<OperationDto> = finder.findAll()
}
