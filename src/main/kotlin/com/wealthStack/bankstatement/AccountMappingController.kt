package com.wealthStack.bankstatement

import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/account-mappings")
class AccountMappingController(val mapper: AccountMapper) {

    @PostMapping
    fun create(@RequestBody request: AccountMappingRequest): AccountMapping =
        mapper.create(request.rawAccount, request.displayName)

    @PostMapping("/batch")
    fun createBatch(@RequestBody requests: List<AccountMappingRequest>): List<AccountMapping> =
        mapper.createAll(requests.map { Pair(it.rawAccount, it.displayName) })

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody request: AccountMappingRequest): AccountMapping =
        mapper.update(id, request.rawAccount, request.displayName)

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) = mapper.delete(id)
}

data class AccountMappingRequest(
    val rawAccount: String,
    val displayName: String
)
