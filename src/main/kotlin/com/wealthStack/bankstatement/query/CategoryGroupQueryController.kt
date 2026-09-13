package com.wealthStack.bankstatement.query

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/category-groups")
class CategoryGroupQueryController(val finder: CategoryGroupFinder) {

    @GetMapping
    fun getAll(): List<CategoryGroupDto> = finder.findAll()
}
