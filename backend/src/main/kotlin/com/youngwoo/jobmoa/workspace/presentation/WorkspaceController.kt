package com.youngwoo.jobmoa.workspace.presentation

import com.youngwoo.jobmoa.workspace.application.WorkspaceService
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import org.springframework.http.ResponseEntity
import java.nio.file.Files
import java.time.LocalDate

@RestController @RequestMapping("/api/workspace")
class WorkspaceController(private val service: WorkspaceService) {
    @GetMapping fun overview() = service.overview()
    @PostMapping("/reset") fun reset(@RequestBody r: ResetRequest) = service.reset(r.scope, r.confirmation)
    @PostMapping("/captures/cleanup") fun cleanup(@RequestBody r: ConfirmationRequest): Map<String, Int> {
        require(r.confirmation == "정리") { "확인 문구 '정리'를 입력하세요." }
        return mapOf("deleted" to service.garbageCollectFiles())
    }
    @GetMapping("/backup") fun backup(): ResponseEntity<StreamingResponseBody> {
        val file = service.backup()
        return ResponseEntity.ok().header("Content-Disposition", "attachment; filename=jobmoa-${LocalDate.now()}.zip")
            .header("Cache-Control", "no-store").header("Content-Type", "application/zip").contentLength(Files.size(file))
            .body(StreamingResponseBody { out -> try { Files.copy(file, out) } finally { Files.deleteIfExists(file) } })
    }
    @PostMapping("/backups/preview") fun preview(@RequestParam("file") file: MultipartFile) = service.preview(file)
    @DeleteMapping("/backups/{token}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) fun discard(@PathVariable token: String) = service.discard(token)
    @PostMapping("/backups/{token}/restore") fun restore(@PathVariable token: String, @RequestBody r: ConfirmationRequest) = service.restore(token, r.confirmation)
}
data class ResetRequest(val scope: String, val confirmation: String)
data class ConfirmationRequest(val confirmation: String)
