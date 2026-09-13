package com.wealthStack.bankstatement

import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/** Whole-dataset export / import / wipe — see [DataBackupService] for the merge and replace semantics. */
@RestController
@RequestMapping("/api/v1/data")
class DataBackupController(private val service: DataBackupService) {

    /** Downloads everything as a `wealthstack-backup-<date>.json` attachment. */
    @GetMapping("/export")
    fun exportData(): ResponseEntity<DataBackup> {
        val disposition = ContentDisposition.attachment().filename("wealthstack-backup-${LocalDate.now()}.json").build()
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .body(service.export())
    }

    /** Loads a backup; `replace=true` wipes all existing data first. */
    @PostMapping("/import", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun importData(
        @RequestBody backup: DataBackup,
        @RequestParam(defaultValue = "false") replace: Boolean
    ): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(service.importBackup(backup, replace))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to (e.message ?: "Unexpected error")))
        }
    }

    /** Deletes ALL data — operations, categories, groups and account mappings — leaving an empty installation. */
    @DeleteMapping
    fun clearAll(): DataClearResult = service.clearAll()
}
