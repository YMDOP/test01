package com.love.interviewforge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 模型定价，单位：元 / 百万 token。
 */
@Entity
@Table(name = "ai_pricing")
public class AiPricing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model", nullable = false, length = 128)
    private String model;

    @Column(name = "input_miss_price", nullable = false)
    private Double inputMissPrice;

    @Column(name = "input_hit_price", nullable = false)
    private Double inputHitPrice;

    @Column(name = "cache_write_price", nullable = false)
    private Double cacheWritePrice;

    @Column(name = "output_price", nullable = false)
    private Double outputPrice;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency = "CNY";

    @Column(name = "effective_from", nullable = false)
    private String effectiveFrom;

    public AiPricing() {
    }

    public Long getId() {
        return id;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Double getInputMissPrice() {
        return inputMissPrice;
    }

    public void setInputMissPrice(Double inputMissPrice) {
        this.inputMissPrice = inputMissPrice;
    }

    public Double getInputHitPrice() {
        return inputHitPrice;
    }

    public void setInputHitPrice(Double inputHitPrice) {
        this.inputHitPrice = inputHitPrice;
    }

    public Double getCacheWritePrice() {
        return cacheWritePrice;
    }

    public void setCacheWritePrice(Double cacheWritePrice) {
        this.cacheWritePrice = cacheWritePrice;
    }

    public Double getOutputPrice() {
        return outputPrice;
    }

    public void setOutputPrice(Double outputPrice) {
        this.outputPrice = outputPrice;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(String effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }
}
