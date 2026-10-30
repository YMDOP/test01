package com.love.interviewforge.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.love.interviewforge.domain.AiUsage;
import com.love.interviewforge.domain.AiUsageRepository;

/**
 * 用量记账：成功与失败都要落一行，ai_usage 是费用的唯一事实源。
 */
@Service
public class AiUsageRecorder {

    private final AiUsageRepository repository;

    public AiUsageRecorder(AiUsageRepository repository) {
        this.repository = repository;
    }

    /**
     * 独立事务：即使外层业务回滚，调用记录也必须留下。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AiUsage record(String purpose,
                          String model,
                          TokenUsage usage,
                          double estCost,
                          long latencyMs,
                          String refType,
                          Long refId,
                          String status,
                          String error) {
        TokenUsage safe = usage == null ? TokenUsage.empty() : usage;

        AiUsage row = new AiUsage();
        row.setTs(Instant.now().toString());
        row.setPurpose(purpose);
        row.setModel(model);
        row.setInputTokens(safe.inputTokens());
        row.setOutputTokens(safe.outputTokens());
        row.setCacheReadTokens(safe.cacheReadTokens());
        row.setCacheWriteTokens(safe.cacheWriteTokens());
        row.setTotalTokens(safe.totalTokens());
        row.setEstCost(estCost);
        row.setLatencyMs(latencyMs);
        row.setRefType(refType);
        row.setRefId(refId);
        row.setStatus(status);
        row.setError(error);
        return repository.save(row);
    }
}
