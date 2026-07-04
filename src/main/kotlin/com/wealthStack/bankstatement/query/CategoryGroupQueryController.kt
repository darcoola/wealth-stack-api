package com.wealthStack.bankstatement.query

import com.wealthStack.security.PartyContext
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/category-groups")
class CategoryGroupQueryController(val finder: CategoryGroupFinder) {

    @GetMapping
    fun getAll(ctx: PartyContext): List<CategoryGroupDto> = finder.findAll(ctx.partyId)
}
