package com.love.interviewforge.api;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.love.interviewforge.common.ApiResponse;
import com.love.interviewforge.domain.Document;
import com.love.interviewforge.service.DocumentService;

/**
 * 文档扫描与导入。扫描只读，导入落库。
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    public record DirRequest(String dir) {
    }

    public record DocItem(Long id, String filename, String ext, long size, String parseStatus,
                          String parseError, int chunkCount) {
    }

    @PostMapping("/scan")
    public ApiResponse<DocumentService.ScanSummary> scan(@RequestBody DirRequest request) {
        return ApiResponse.ok(documentService.scan(requireDir(request)));
    }

    @PostMapping("/import")
    public ApiResponse<DocumentService.ImportSummary> importDocuments(@RequestBody DirRequest request) {
        return ApiResponse.ok(documentService.importDocuments(requireDir(request)));
    }

    @GetMapping
    public ApiResponse<List<DocItem>> list() {
        List<DocItem> items = documentService.list().stream()
                .map(this::toItem)
                .toList();
        return ApiResponse.ok(items);
    }

    private DocItem toItem(Document document) {
        return new DocItem(document.id, document.filename, document.ext, document.size,
                document.parseStatus, document.parseError, document.chunkCount);
    }

    private String requireDir(DirRequest request) {
        if (request == null || request.dir() == null || request.dir().isBlank()) {
            throw new com.love.interviewforge.common.BusinessException(1002, "缺少目录路径");
        }
        return request.dir().trim();
    }
}
