package com.wealthStack.bankstatement

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
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
        @RequestParam("bankName") bankName: String
    ): ResponseEntity<Any> {
        return try {
            val content = String(file.bytes, Charsets.UTF_8)
            val result: ImportResult = importer.importStatement(bankName, file.originalFilename ?: "unknown", content)
            ResponseEntity.ok(result)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to (e.message ?: "Unexpected error")))
        }
    }
}
