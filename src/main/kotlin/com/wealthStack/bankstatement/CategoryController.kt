package com.wealthStack.bankstatement

import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/categories")
class CategoryController(val service: CategoryService) {

    @PostMapping
    fun create(@RequestBody request: CategoryRequest): Category =
        service.create(request.name)

    @PutMapping("/{id}")
    fun rename(@PathVariable id: Long, @RequestBody request: CategoryRequest): Category =
        service.rename(id, request.name)

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) = service.delete(id)
}

data class CategoryRequest(val name: String)
