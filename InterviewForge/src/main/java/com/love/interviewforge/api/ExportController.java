package com.love.interviewforge.api;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.love.interviewforge.common.ApiResponse;
import com.love.interviewforge.service.ExportService;

/**
 * 题库导出：Markdown / Excel，产物落在 data/exports/。
 */
@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @PostMapping
    public ApiResponse<ExportService.ExportResult> export(
            @RequestBody(required = false) ExportService.ExportRequest request) {
        return ApiResponse.ok(exportService.export(
                request == null ? new ExportService.ExportRequest("MD", null, true, true) : request));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        ExportService.FilePayload payload = exportService.loadFile(id);
        String encoded = URLEncoder.encode(payload.filename(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"export\"; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(payload.contentType()))
                .body(payload.bytes());
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> history() {
        return ApiResponse.ok(exportService.history());
    }
}
