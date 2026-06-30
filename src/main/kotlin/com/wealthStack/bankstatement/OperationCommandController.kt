package com.wealthStack.bankstatement

import com.wealthStack.bankstatement.query.OperationDto
import com.wealthStack.bankstatement.query.toDto
import org.springframework.web.bind.annotation.*

/**
 * Mutations on individual operations. Today this is just (re)assigning a category; imports remain in
 * [BankStatementController].
 */
@RestController
@RequestMapping("/api/v1/bank-statements/operations")
class OperationCommandController(
    val service: CategoryService,
    val operationService: OperationCommandService,
) {

    @PutMapping("/{id}/category")
    fun assignCategory(@PathVariable id: Long, @RequestBody request: AssignCategoryRequest): OperationDto =
        service.assignToOperation(id, request.categoryId).toDto()

    @PutMapping("/category")
    fun assignCategoryBulk(@RequestBody request: BulkAssignCategoryRequest): List<OperationDto> =
        service.assignToOperations(request.operationIds, request.categoryId).map { it.toDto() }

    @DeleteMapping
    fun deleteBulk(@RequestBody request: BulkDeleteRequest) =
        operationService.deleteAll(request.operationIds)
}

data class AssignCategoryRequest(val categoryId: Long?)

data class BulkAssignCategoryRequest(val operationIds: List<Long>, val categoryId: Long?)

data class BulkDeleteRequest(val operationIds: List<Long>)
