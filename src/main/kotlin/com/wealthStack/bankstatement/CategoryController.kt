package com.wealthStack.bankstatement

import com.wealthStack.security.PartyContext
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/categories")
class CategoryController(val service: CategoryService) {

    @PostMapping
    fun create(ctx: PartyContext, @RequestBody request: CategoryRequest): Category =
        service.create(ctx.partyId, request.name, request.groupId)

    @PostMapping("/batch")
    fun createBatch(ctx: PartyContext, @RequestBody requests: List<CategoryRequest>): List<Category> =
        service.createAll(ctx.partyId, requests.map { Pair(it.name, it.groupId) })

    @PutMapping("/{id}")
    fun update(ctx: PartyContext, @PathVariable id: Long, @RequestBody request: CategoryRequest): Category =
        service.update(ctx.partyId, id, request.name, SetGroup(request.groupId))

    @DeleteMapping("/{id}")
    fun delete(ctx: PartyContext, @PathVariable id: Long) = service.delete(ctx.partyId, id)
}

data class CategoryRequest(val name: String, val groupId: Long? = null)
