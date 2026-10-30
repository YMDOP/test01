package com.love.interviewforge.api;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.love.interviewforge.common.ApiResponse;
import com.love.interviewforge.service.GenerateService;

/**
 * 题库生成：预估、创建、SSE 进度、重置、统计。
 */
@RestController
@RequestMapping("/api/generate")
public class GenerateController {

    private final GenerateService generateService;

    public GenerateController(GenerateService generateService) {
        this.generateService = generateService;
    }

    public record CreateRequest(Integer maxChunks, Boolean confirm) {
    }

    public record ResetRequest(Boolean keepCategories) {
    }

    @GetMapping("/estimate")
    public ApiResponse<GenerateService.Estimate> estimate() {
        return ApiResponse.ok(generateService.estimate(0));
    }

    @PostMapping("/jobs")
    public ApiResponse<GenerateService.CreateResult> create(@RequestBody(required = false) CreateRequest request) {
        int maxChunks = request == null || request.maxChunks() == null ? 0 : request.maxChunks();
        boolean confirm = request != null && Boolean.TRUE.equals(request.confirm());
        return ApiResponse.ok(generateService.create(maxChunks, confirm));
    }

    @GetMapping("/jobs/{id}")
    public ApiResponse<GenerateService.JobView> job(@PathVariable String id) {
        return ApiResponse.ok(generateService.view(id));
    }

    @GetMapping(value = "/jobs/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String id) {
        return generateService.stream(id);
    }

    @PostMapping("/reset")
    public ApiResponse<GenerateService.ResetResult> reset(@RequestBody(required = false) ResetRequest request) {
        boolean keepCategories = request != null && Boolean.TRUE.equals(request.keepCategories());
        return ApiResponse.ok(generateService.reset(keepCategories));
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        return ApiResponse.ok(generateService.summary());
    }
}
