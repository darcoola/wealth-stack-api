package com.wealthStack.bankstatement

import com.wealthStack.security.PartyContext
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/account-mappings")
class AccountMappingController(val mapper: AccountMapper) {

    @PostMapping
    fun create(ctx: PartyContext, @RequestBody request: AccountMappingRequest): AccountMapping =
        mapper.create(ctx.partyId, request.rawAccount, request.displayName)

    @PostMapping("/batch")
    fun createBatch(ctx: PartyContext, @RequestBody requests: List<AccountMappingRequest>): List<AccountMapping> =
        mapper.createAll(ctx.partyId, requests.map { Pair(it.rawAccount, it.displayName) })

    @PutMapping("/{id}")
    fun update(ctx: PartyContext, @PathVariable id: Long, @RequestBody request: AccountMappingRequest): AccountMapping =
        mapper.update(ctx.partyId, id, request.rawAccount, request.displayName)

    @DeleteMapping("/{id}")
    fun delete(ctx: PartyContext, @PathVariable id: Long) = mapper.delete(ctx.partyId, id)
}

data class AccountMappingRequest(
    val rawAccount: String,
    val displayName: String
)
