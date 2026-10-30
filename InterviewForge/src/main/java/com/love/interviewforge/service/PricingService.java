package com.love.interviewforge.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.love.interviewforge.domain.AiPricing;
import com.love.interviewforge.domain.AiPricingRepository;

/**
 * 模型定价与费用计算。
 *
 * <p>费用公式固定为三段式，禁止简化为「总 token × 单价」：
 * input×miss + cacheRead×hit + cacheWrite×write + output×out，再除以一百万。</p>
 */
@Service
public class PricingService {

    private static final Logger log = LoggerFactory.getLogger(PricingService.class);

    /** 兜底价：表里查不到时使用，避免除零或空指针。 */
    private static final Price FALLBACK = new Price(2.00, 0.20, 2.00, 8.00, "CNY");

    private static final String DEFAULT_MODEL = "deepseek-chat";

    private final AiPricingRepository repository;

    public PricingService(AiPricingRepository repository) {
        this.repository = repository;
    }

    public record Price(double inputMiss, double inputHit, double cacheWrite, double output, String currency) {
    }

    /** 首次启动时写入示例价，真实价格在设置页修改。 */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedDefaultPrice() {
        if (repository.count() > 0) {
            return;
        }
        AiPricing pricing = new AiPricing();
        pricing.setModel(DEFAULT_MODEL);
        pricing.setInputMissPrice(2.00);
        pricing.setInputHitPrice(0.20);
        pricing.setCacheWritePrice(2.00);
        pricing.setOutputPrice(8.00);
        pricing.setCurrency("CNY");
        pricing.setEffectiveFrom("2026-01-01T00:00:00Z");
        repository.save(pricing);
        log.info("已写入示例定价（{}），请到设置页按实际价目调整", DEFAULT_MODEL);
    }

    @Transactional(readOnly = true)
    public Price priceFor(String model) {
        String now = Instant.now().toString();
        return repository
                .findFirstByModelAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(model, now)
                .or(() -> repository.findFirstByModelAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        DEFAULT_MODEL, now))
                .map(item -> new Price(
                        item.getInputMissPrice(),
                        item.getInputHitPrice(),
                        item.getCacheWritePrice(),
                        item.getOutputPrice(),
                        item.getCurrency()))
                .orElse(FALLBACK);
    }

    public double cost(TokenUsage usage, Price price) {
        double raw = usage.inputTokens() * price.inputMiss()
                + usage.cacheReadTokens() * price.inputHit()
                + usage.cacheWriteTokens() * price.cacheWrite()
                + usage.outputTokens() * price.output();
        return raw / 1_000_000d;
    }
}
