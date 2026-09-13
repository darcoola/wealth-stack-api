package com.wealthStack.bankstatement

import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/categories")
class CategoryController(val service: CategoryService) {

    @PostMapping
    fun create(@RequestBody request: CategoryRequest): Category =
        service.create(request.name, request.groupId)

    @PostMapping("/batch")
    fun createBatch(@RequestBody requests: List<CategoryRequest>): List<Category> =
        service.createAll(requests.map { Pair(it.name, it.groupId) })

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody request: CategoryRequest): Category =
        service.update(id, request.name, SetGroup(request.groupId))

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) = service.delete(id)
}

data class CategoryRequest(val name: String, val groupId: Long? = null)
