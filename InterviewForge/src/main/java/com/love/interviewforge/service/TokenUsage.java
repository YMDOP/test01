package com.love.interviewforge.service;

/**
 * 一次调用上游返回的 token 用量。
 *
 * <p>inputTokens 表示「未命中缓存的输入」，cacheReadTokens 表示命中缓存的部分，
 * 两者之和大致等于上游的 prompt_tokens。</p>
 */
public record TokenUsage(
        int inputTokens,
        int outputTokens,
        int cacheReadTokens,
        int cacheWriteTokens,
        int totalTokens) {

    public static TokenUsage empty() {
        return new TokenUsage(0, 0, 0, 0, 0);
    }
}
