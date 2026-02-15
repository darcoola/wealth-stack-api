package com.wealthStack.bankstatement

import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/account-mappings")
class AccountMappingController(val mapper: AccountMapper) {

    @PutMapping
    fun upsert(@RequestBody request: AccountMappingRequest): AccountMapping =
        mapper.upsert(request.rawAccount, request.displayName)
}

data class AccountMappingRequest(
    val rawAccount: String,
    val displayName: String
)
