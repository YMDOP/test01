package com.love.interviewforge.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.love.interviewforge.common.BusinessException;
import com.love.interviewforge.common.Crypto;
import com.love.interviewforge.domain.AiConfig;
import com.love.interviewforge.domain.AiConfigRepository;
import com.love.interviewforge.domain.AiPricing;
import com.love.interviewforge.domain.AiPricingRepository;

/**
 * AI 接入配置。Key 的取值优先级：环境变量 → 库内密文 → 未配置。
 */
@Service
public class AiConfigService {

    public static final String ENV_API_KEY = "INTERVIEWFORGE_AI_API_KEY";
    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    private static final String DEFAULT_MODEL = "deepseek-chat";

    private final AiConfigRepository configRepository;
    private final AiPricingRepository pricingRepository;

    public AiConfigService(AiConfigRepository configRepository, AiPricingRepository pricingRepository) {
        this.configRepository = configRepository;
        this.pricingRepository = pricingRepository;
    }

    /** 对外展示用：Key 只给掩码与来源。 */
    public record View(String baseUrl,
                       String model,
                       String keyMasked,
                       String keySource,
                       double temperature,
                       int maxTokens,
                       boolean enabled,
                       String updatedAt) {
    }

    /** 内部使用：解析后的真实 Key 与来源。 */
    public record Resolved(String baseUrl, String model, String apiKey, String source) {
    }

    /** 更新请求：apiKey 为空表示「不修改」。 */
    public record Patch(String baseUrl,
                        String model,
                        String apiKey,
                        Double temperature,
                        Integer maxTokens,
                        Boolean enabled,
                        Double inputMissPrice,
                        Double inputHitPrice,
                        Double cacheWritePrice,
                        Double outputPrice) {
    }

    @Transactional
    public AiConfig loadOrCreate() {
        return configRepository.findById(AiConfig.SINGLETON_ID).orElseGet(() -> {
            AiConfig created = new AiConfig();
            created.setBaseUrl(DEFAULT_BASE_URL);
            created.setModel(DEFAULT_MODEL);
            created.setTemperature(0.4);
            created.setMaxTokens(4096);
            created.setEnabled(Boolean.TRUE);
            created.setUpdatedAt(Instant.now().toString());
            return configRepository.save(created);
        });
    }

    @Transactional
    public View view() {
        AiConfig config = loadOrCreate();
        Resolved resolved = resolve(config);
        return new View(
                config.getBaseUrl(),
                config.getModel(),
                Crypto.mask(resolved.apiKey()),
                resolved.source(),
                config.getTemperature(),
                config.getMaxTokens(),
                Boolean.TRUE.equals(config.getEnabled()),
                config.getUpdatedAt());
    }

    @Transactional
    public Resolved resolve() {
        return resolve(loadOrCreate());
    }

    @Transactional
    public View update(Patch patch) {
        AiConfig config = loadOrCreate();

        if (patch.baseUrl() != null && !patch.baseUrl().isBlank()) {
            config.setBaseUrl(patch.baseUrl().trim());
        }
        if (patch.model() != null && !patch.model().isBlank()) {
            config.setModel(patch.model().trim());
        }
        if (patch.temperature() != null) {
            config.setTemperature(patch.temperature());
        }
        if (patch.maxTokens() != null) {
            config.setMaxTokens(patch.maxTokens());
        }
        if (patch.enabled() != null) {
            config.setEnabled(patch.enabled());
        }
        // 空串或掩码回显值都视为「不修改」
        if (patch.apiKey() != null && !patch.apiKey().isBlank() && !patch.apiKey().contains("****")) {
            config.setApiKeyEnc(Crypto.encrypt(patch.apiKey().trim()));
        }
        config.setUpdatedAt(Instant.now().toString());
        configRepository.save(config);

        if (patch.inputMissPrice() != null && patch.outputPrice() != null) {
            upsertPricing(config.getModel(), patch);
        }
        return view();
    }

    private void upsertPricing(String model, Patch patch) {
        AiPricing pricing = pricingRepository
                .findFirstByModelAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(model, Instant.now().toString())
                .orElseGet(AiPricing::new);
        pricing.setModel(model);
        pricing.setInputMissPrice(patch.inputMissPrice());
        pricing.setInputHitPrice(patch.inputHitPrice() == null ? patch.inputMissPrice() : patch.inputHitPrice());
        pricing.setCacheWritePrice(
                patch.cacheWritePrice() == null ? patch.inputMissPrice() : patch.cacheWritePrice());
        pricing.setOutputPrice(patch.outputPrice());
        pricing.setCurrency("CNY");
        pricing.setEffectiveFrom(Instant.now().toString());
        pricingRepository.save(pricing);
    }

    private Resolved resolve(AiConfig config) {
        // 约定变量优先；其次兼容 DeepSeek 官方常用名 DEEPSEEK_API_KEY
        for (String envName : new String[] {ENV_API_KEY, "DEEPSEEK_API_KEY"}) {
            String envKey = System.getenv(envName);
            if (envKey != null && !envKey.isBlank()) {
                return new Resolved(config.getBaseUrl(), config.getModel(), envKey.trim(), "ENV");
            }
        }
        String decrypted = Crypto.decrypt(config.getApiKeyEnc());
        if (decrypted != null && !decrypted.isBlank()) {
            return new Resolved(config.getBaseUrl(), config.getModel(), decrypted, "DB");
        }
        return new Resolved(config.getBaseUrl(), config.getModel(), null, "NONE");
    }

    /** 供测试断言使用，避免把 Key 写进日志。 */
    public static void requireConfigured(Resolved resolved) {
        if (resolved.apiKey() == null || resolved.apiKey().isBlank()) {
            throw new BusinessException(3002, "尚未配置 API Key：请设置环境变量 " + ENV_API_KEY + " 或在设置页填写");
        }
    }
}
