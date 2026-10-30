package com.love.interviewforge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * AI 接入配置，单行（id 固定 1）。
 */
@Entity
@Table(name = "ai_config")
public class AiConfig {

    public static final int SINGLETON_ID = 1;

    @Id
    @Column(name = "id", nullable = false)
    private Integer id = SINGLETON_ID;

    @Column(name = "base_url", nullable = false, length = 512)
    private String baseUrl;

    @Column(name = "model", nullable = false, length = 128)
    private String model;

    /** AES-GCM 密文，非明文。 */
    @Column(name = "api_key_enc", columnDefinition = "TEXT")
    private String apiKeyEnc;

    @Column(name = "temperature", nullable = false)
    private Double temperature = 0.4;

    @Column(name = "max_tokens", nullable = false)
    private Integer maxTokens = 4096;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    @Column(name = "updated_at", nullable = false)
    private String updatedAt;

    public AiConfig() {
    }

    public Integer getId() {
        return id;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getApiKeyEnc() {
        return apiKeyEnc;
    }

    public void setApiKeyEnc(String apiKeyEnc) {
        this.apiKeyEnc = apiKeyEnc;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
