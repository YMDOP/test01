package com.love.interviewforge.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.love.interviewforge.common.BusinessException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 与 OpenAI 兼容的 Chat Completions 接口通信。
 *
 * <p>所有调用都必须经过这里，保证用量一定落库、Key 一定不落日志。</p>
 */
@Service
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    private final ObjectMapper objectMapper;
    private final AiConfigService configService;
    private final PricingService pricingService;
    private final AiUsageRecorder usageRecorder;

    public AiClient(ObjectMapper objectMapper,
                    AiConfigService configService,
                    PricingService pricingService,
                    AiUsageRecorder usageRecorder) {
        this.objectMapper = objectMapper;
        this.configService = configService;
        this.pricingService = pricingService;
        this.usageRecorder = usageRecorder;
    }

    public record TestResult(String model,
                             String keySource,
                             long latencyMs,
                             TokenUsage usage,
                             double estCost,
                             String currency) {
    }

    /**
     * 连通性测试：发一条最小请求，返回耗时、用量与估算费用。
     * 失败也会写一条 ai_usage（status=FAILED）。
     */
    public TestResult test() {
        AiConfigService.Resolved config = configService.resolve();
        AiConfigService.requireConfigured(config);

        String url = trimTrailingSlash(config.baseUrl()) + "/chat/completions";

        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", config.model(),
                    "messages", List.of(Map.of("role", "user", "content", "ping")),
                    "max_tokens", 16,
                    "temperature", 0,
                    "stream", false));
        } catch (RuntimeException e) {
            throw new BusinessException(3001, "请求体构造失败");
        }

        long startNanos = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long latencyMs = elapsed(startNanos);

            if (response.statusCode() / 100 != 2) {
                usageRecorder.record("CONNECTIVITY_TEST", config.model(), TokenUsage.empty(), 0d,
                        latencyMs, "AI_CONFIG", null, "FAILED", "HTTP " + response.statusCode());
                // 不回显上游原文，避免把 Key 或账户信息带进响应
                throw new BusinessException(3001,
                        "AI 服务返回 " + response.statusCode() + "，请检查 Base URL / 模型 / Key 是否正确");
            }

            TokenUsage usage;
            try {
                usage = parseUsage(response.body());
            } catch (RuntimeException e) {
                usageRecorder.record("CONNECTIVITY_TEST", config.model(), TokenUsage.empty(), 0d,
                        latencyMs, "AI_CONFIG", null, "FAILED", "unparsable response");
                throw new BusinessException(3001, "AI 返回内容无法解析");
            }

            PricingService.Price price = pricingService.priceFor(config.model());
            double cost = pricingService.cost(usage, price);

            usageRecorder.record("CONNECTIVITY_TEST", config.model(), usage, cost,
                    latencyMs, "AI_CONFIG", null, "OK", null);

            log.info("AI 连通性测试成功: model={} latency={}ms tokens={}",
                    config.model(), latencyMs, usage.totalTokens());
            return new TestResult(config.model(), config.source(), latencyMs, usage, cost, price.currency());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long latencyMs = elapsed(startNanos);
            usageRecorder.record("CONNECTIVITY_TEST", config.model(), TokenUsage.empty(), 0d,
                    latencyMs, "AI_CONFIG", null, "FAILED", "interrupted");
            throw new BusinessException(3001, "AI 请求被中断");
        } catch (IOException e) {
            long latencyMs = elapsed(startNanos);
            usageRecorder.record("CONNECTIVITY_TEST", config.model(), TokenUsage.empty(), 0d,
                    latencyMs, "AI_CONFIG", null, "FAILED", e.getClass().getSimpleName());
            throw new BusinessException(3001, "无法连接 AI 服务：" + e.getMessage());
        }
    }

    private TokenUsage parseUsage(String body) {
        JsonNode root = objectMapper.readTree(body);
        JsonNode usage = root.path("usage");

        int promptTokens = usage.path("prompt_tokens").asInt(0);
        int completionTokens = usage.path("completion_tokens").asInt(0);
        int totalTokens = usage.path("total_tokens").asInt(promptTokens + completionTokens);
        int cacheRead = usage.path("prompt_cache_hit_tokens").asInt(0);
        int cacheMiss = usage.path("prompt_cache_miss_tokens").asInt(-1);
        int inputTokens = cacheMiss >= 0 ? cacheMiss : Math.max(0, promptTokens - cacheRead);

        return new TokenUsage(inputTokens, completionTokens, cacheRead, 0, totalTokens);
    }

    private static long elapsed(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
