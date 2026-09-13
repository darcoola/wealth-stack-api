package com.wealthStack.bankstatement.query

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/account-mappings")
class AccountMappingQueryController(val finder: AccountMappingFinder) {

    @GetMapping
    fun getAll(): List<AccountMappingDto> = finder.findAll()
}
