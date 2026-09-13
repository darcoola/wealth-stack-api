package com.wealthStack.bankstatement

import com.wealthStack.bankstatement.query.toDto
import org.springframework.http.HttpStatus
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
        @RequestParam("file") file: MultipartFile,
        @RequestParam("bankName", required = false) bankName: String?
    ): ResponseEntity<Any> {
        return try {
            val result: ImportResult = importer.importStatement(bankName, file.originalFilename ?: "unknown", file.bytes)
            ResponseEntity.ok(result)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to (e.message ?: "Unexpected error")))
        }
    }

    /** Ingests already-prepared operation rows as JSON (historical data or unparsed banks). */
    @PostMapping("/operations", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun importOperations(@RequestBody request: ManualOperationsRequest): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(importer.importOperations(request))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to (e.message ?: "Unexpected error")))
        }
    }

    /**
     * Records one hand-entered cash operation (the Operations page's "Add operation" form) and
     * returns it. An entry identical to an existing operation is refused with **409** and the rows it
     * matched, so the UI can ask the user whether it really is a second one; re-sending with
     * `force: true` then saves it. See [StatementImporter.addCashOperation].
     */
    @PostMapping("/operations/manual", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun addCashOperation(@RequestBody request: NewOperationRequest): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(importer.addCashOperation(request).toDto())
        } catch (e: DuplicateOperationException) {
            ResponseEntity.status(HttpStatus.CONFLICT).body(
                mapOf("error" to e.message, "duplicates" to e.existing.map { it.toDto() })
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to (e.message ?: "Unexpected error")))
        }
    }
}
