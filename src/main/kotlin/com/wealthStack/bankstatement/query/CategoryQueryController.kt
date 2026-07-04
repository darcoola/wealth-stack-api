package com.wealthStack.bankstatement.query

import com.wealthStack.security.PartyContext
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/categories")
class CategoryQueryController(val finder: CategoryFinder) {

    @GetMapping
    fun getAll(ctx: PartyContext): List<CategoryDto> = finder.findAll(ctx.partyId)
}
