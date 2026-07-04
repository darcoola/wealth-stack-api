package com.wealthStack.bankstatement

import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/category-groups")
class CategoryGroupController(val service: CategoryGroupService) {

    @PostMapping
    fun create(@RequestBody request: CategoryGroupRequest): CategoryGroup =
        service.create(request.name)

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody request: CategoryGroupRequest): CategoryGroup =
        service.rename(id, request.name)

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) = service.delete(id)
}

data class CategoryGroupRequest(val name: String)
