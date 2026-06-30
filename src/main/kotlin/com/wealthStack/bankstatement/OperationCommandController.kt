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
class OperationCommandController(val service: CategoryService) {

    @PutMapping("/{id}/category")
    fun assignCategory(@PathVariable id: Long, @RequestBody request: AssignCategoryRequest): OperationDto =
        service.assignToOperation(id, request.categoryId).toDto()
}

data class AssignCategoryRequest(val categoryId: Long?)
