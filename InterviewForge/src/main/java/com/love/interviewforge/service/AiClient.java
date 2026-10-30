package com.love.interviewforge.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
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
 * <p>所有调用都必须经过这里，保证用量一定落库、Key 一定不落日志。
 * 单次调用失败自动重试 1 次；两次都失败则抛出，由上层决定是否跳过该批次。</p>
 */
@Service
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);

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

    /** 一次成功的业务调用结果。 */
    public record ChatResult(String content, TokenUsage usage, double cost, long latencyMs) {
    }

    // ------------------------------------------------------------
    // 连通性测试
    // ------------------------------------------------------------

    public TestResult test() {
        AiConfigService.Resolved config = configService.resolve();
        AiConfigService.requireConfigured(config);

        long startNanos = System.nanoTime();
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", config.model(),
                    "messages", List.of(Map.of("role", "user", "content", "ping")),
                    "max_tokens", 16,
                    "temperature", 0,
                    "stream", false));

            HttpResponse<String> response = send(config, body);
            long latencyMs = elapsed(startNanos);

            if (response.statusCode() / 100 != 2) {
                usageRecorder.record("CONNECTIVITY_TEST", config.model(), TokenUsage.empty(), 0d,
                        latencyMs, "AI_CONFIG", null, "FAILED", "HTTP " + response.statusCode());
                throw new BusinessException(3001,
                        "AI 服务返回 " + response.statusCode() + "，请检查 Base URL / 模型 / Key 是否正确");
            }

            TokenUsage usage = parseUsage(response.body());
            PricingService.Price price = pricingService.priceFor(config.model());
            double cost = pricingService.cost(usage, price);
            usageRecorder.record("CONNECTIVITY_TEST", config.model(), usage, cost,
                    latencyMs, "AI_CONFIG", null, "OK", null);
            log.info("AI 连通性测试成功: model={} latency={}ms tokens={}",
                    config.model(), latencyMs, usage.totalTokens());
            return new TestResult(config.model(), config.source(), latencyMs, usage, cost, price.currency());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(3001, "AI 请求被中断");
        } catch (IOException e) {
            long latencyMs = elapsed(startNanos);
            usageRecorder.record("CONNECTIVITY_TEST", config.model(), TokenUsage.empty(), 0d,
                    latencyMs, "AI_CONFIG", null, "FAILED", e.getClass().getSimpleName());
            throw new BusinessException(3001, "无法连接 AI 服务：" + e.getMessage());
        }
    }

    // ------------------------------------------------------------
    // 业务调用（分类 / 出题 / 判卷）
    // ------------------------------------------------------------

    /**
     * 发起一次 chat 调用。失败重试一次；每次尝试都会写 ai_usage 并打一条 AI_CALL 日志。
     */
    public ChatResult chat(String purpose, String systemPrompt, String userPrompt, int maxTokens) {
        AiConfigService.Resolved config = configService.resolve();
        AiConfigService.requireConfigured(config);

        BusinessException lastError = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return doChat(config, purpose, systemPrompt, userPrompt, maxTokens, attempt);
            } catch (BusinessException e) {
                lastError = e;
                if (attempt == 2) {
                    break;
                }
                log.warn("AI 调用失败，准备重试: purpose={} attempt={} reason={}", purpose, attempt, e.getMessage());
            }
        }
        throw lastError == null ? new BusinessException(3001, "AI 调用失败") : lastError;
    }

    private ChatResult doChat(AiConfigService.Resolved config,
                              String purpose,
                              String systemPrompt,
                              String userPrompt,
                              int maxTokens,
                              int attempt) {
        long startNanos = System.nanoTime();

        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userPrompt));

        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", config.model(),
                    "messages", messages,
                    "max_tokens", maxTokens,
                    "temperature", 0.3,
                    "stream", false));

            HttpResponse<String> response = send(config, body);
            long latencyMs = elapsed(startNanos);
            PricingService.Price price = pricingService.priceFor(config.model());

            if (response.statusCode() / 100 != 2) {
                usageRecorder.record(purpose, config.model(), TokenUsage.empty(), 0d, latencyMs,
                        "GENERATE", null, "FAILED", "HTTP " + response.statusCode());
                log.info("AI_CALL purpose={} attempt={} status=FAILED http={}", purpose, attempt, response.statusCode());
                throw new BusinessException(3001, "AI 服务返回 " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode usageNode = root.path("usage");
            TokenUsage usage = parseUsage(usageNode);
            double cost = pricingService.cost(usage, price);
            usageRecorder.record(purpose, config.model(), usage, cost, latencyMs,
                    "GENERATE", null, "OK", null);

            String content = root.path("choices").path(0).path("message").path("content").asString("");
            log.info("AI_CALL purpose={} attempt={} status=OK tokens={} cost={} latency={}ms",
                    purpose, attempt, usage.totalTokens(), cost, latencyMs);
            return new ChatResult(content, usage, cost, latencyMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(3001, "AI 请求被中断");
        } catch (IOException e) {
            long latencyMs = elapsed(startNanos);
            usageRecorder.record(purpose, config.model(), TokenUsage.empty(), 0d, latencyMs,
                    "GENERATE", null, "FAILED", e.getClass().getSimpleName());
            log.info("AI_CALL purpose={} attempt={} status=FAILED io={}", purpose, attempt, e.getClass().getSimpleName());
            throw new BusinessException(3001, "无法连接 AI 服务：" + e.getMessage());
        }
    }

    private HttpResponse<String> send(AiConfigService.Resolved config, String body)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(trimTrailingSlash(config.baseUrl()) + "/chat/completions"))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private TokenUsage parseUsage(String body) {
        return parseUsage(objectMapper.readTree(body).path("usage"));
    }

    private TokenUsage parseUsage(JsonNode usage) {
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
