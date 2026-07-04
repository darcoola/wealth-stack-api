package com.wealthStack.bankstatement

import com.wealthStack.bankstatement.query.OperationDto
import com.wealthStack.bankstatement.query.toDto
import com.wealthStack.security.PartyContext
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
    fun assignCategory(
        ctx: PartyContext,
        @PathVariable id: Long,
        @RequestBody request: AssignCategoryRequest,
    ): OperationDto =
        service.assignToOperation(ctx.partyId, id, request.categoryId).toDto()

    @PutMapping("/{id}/additional-info")
    fun updateAdditionalInfo(
        ctx: PartyContext,
        @PathVariable id: Long,
        @RequestBody request: UpdateAdditionalInfoRequest,
    ): OperationDto =
        operationService.updateAdditionalInfo(ctx.partyId, id, request.additionalInfo).toDto()

    @PutMapping("/category")
    fun assignCategoryBulk(ctx: PartyContext, @RequestBody request: BulkAssignCategoryRequest): List<OperationDto> =
        service.assignToOperations(ctx.partyId, request.operationIds, request.categoryId).map { it.toDto() }

    @DeleteMapping
    fun deleteBulk(ctx: PartyContext, @RequestBody request: BulkDeleteRequest) =
        operationService.deleteAll(ctx.partyId, request.operationIds)

    @DeleteMapping("/all")
    fun deleteEverything(ctx: PartyContext): Map<String, Long> =
        mapOf("deletedCount" to operationService.deleteEverything(ctx.partyId))

    @PutMapping("/{id}/verify")
    fun acceptPrediction(ctx: PartyContext, @PathVariable id: Long): OperationDto =
        service.acceptPrediction(ctx.partyId, id).toDto()

    @PutMapping("/verify")
    fun acceptPredictionsBulk(ctx: PartyContext, @RequestBody request: BulkVerifyRequest): List<OperationDto> =
        service.acceptPredictions(ctx.partyId, request.operationIds).map { it.toDto() }

    @PostMapping("/sync")
    fun syncToSearch(ctx: PartyContext): Map<String, Int> =
        mapOf("syncedCount" to service.syncCategorizedOperationsToSearch(ctx.partyId))
}

data class AssignCategoryRequest(val categoryId: Long?)

data class UpdateAdditionalInfoRequest(val additionalInfo: String?)

data class BulkAssignCategoryRequest(val operationIds: List<Long>, val categoryId: Long?)

data class BulkDeleteRequest(val operationIds: List<Long>)

data class BulkVerifyRequest(val operationIds: List<Long>)
