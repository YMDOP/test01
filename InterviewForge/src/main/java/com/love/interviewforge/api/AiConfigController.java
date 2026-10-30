package com.love.interviewforge.api;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.love.interviewforge.common.ApiResponse;
import com.love.interviewforge.service.AiClient;
import com.love.interviewforge.service.AiConfigService;

/**
 * AI 配置与连通性测试。Key 只以掩码形式出站。
 */
@RestController
@RequestMapping("/api/settings/ai")
public class AiConfigController {

    private final AiConfigService configService;
    private final AiClient aiClient;

    public AiConfigController(AiConfigService configService, AiClient aiClient) {
        this.configService = configService;
        this.aiClient = aiClient;
    }

    @GetMapping
    public ApiResponse<AiConfigService.View> get() {
        return ApiResponse.ok(configService.view());
    }

    @PutMapping
    public ApiResponse<AiConfigService.View> update(@RequestBody AiConfigService.Patch patch) {
        return ApiResponse.ok(configService.update(patch));
    }

    @PostMapping("/test")
    public ApiResponse<Map<String, Object>> test() {
        AiClient.TestResult result = aiClient.test();

        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("inputTokens", result.usage().inputTokens());
        usage.put("outputTokens", result.usage().outputTokens());
        usage.put("cacheReadTokens", result.usage().cacheReadTokens());
        usage.put("cacheWriteTokens", result.usage().cacheWriteTokens());
        usage.put("totalTokens", result.usage().totalTokens());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("model", result.model());
        body.put("keySource", result.keySource());
        body.put("latencyMs", result.latencyMs());
        body.put("usage", usage);
        body.put("estCost", result.estCost());
        body.put("currency", result.currency());
        return ApiResponse.ok(body);
    }
}
