package com.wealthStack.bankstatement

import com.wealthStack.security.PartyContext
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/category-groups")
class CategoryGroupController(val service: CategoryGroupService) {

    @PostMapping
    fun create(ctx: PartyContext, @RequestBody request: CategoryGroupRequest): CategoryGroup =
        service.create(ctx.partyId, request.name)

    @PutMapping("/{id}")
    fun update(ctx: PartyContext, @PathVariable id: Long, @RequestBody request: CategoryGroupRequest): CategoryGroup =
        service.rename(ctx.partyId, id, request.name)

    @DeleteMapping("/{id}")
    fun delete(ctx: PartyContext, @PathVariable id: Long) = service.delete(ctx.partyId, id)
}

data class CategoryGroupRequest(val name: String)
