package com.wealthStack.bankstatement

import com.wealthStack.security.PartyContext
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1/bank-statements")
class BankStatementController(val importer: StatementImporter) {

    @PostMapping
    fun uploadStatement(
        ctx: PartyContext,
        @RequestParam("file") file: MultipartFile,
        @RequestParam("bankName", required = false) bankName: String?
    ): ResponseEntity<Any> {
        return try {
            val result: ImportResult =
                importer.importStatement(ctx.partyId, bankName, file.originalFilename ?: "unknown", file.bytes)
            ResponseEntity.ok(result)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to (e.message ?: "Unexpected error")))
        }
    }

    /** Ingests already-prepared operation rows as JSON (historical data or unparsed banks). */
    @PostMapping("/operations", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun importOperations(ctx: PartyContext, @RequestBody request: ManualOperationsRequest): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(importer.importOperations(ctx.partyId, request))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to (e.message ?: "Unexpected error")))
        }
    }
}
