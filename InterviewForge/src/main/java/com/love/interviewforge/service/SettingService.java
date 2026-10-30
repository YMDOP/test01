package com.love.interviewforge.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.love.interviewforge.domain.AppSetting;
import com.love.interviewforge.domain.AppSettingRepository;

/**
 * 配置读写。读取时把默认值合并进来，保证前端永远拿得到完整配置。
 */
@Service
public class SettingService {

    /** 背景图相关默认值，必须与前端 tokens 的默认表现一致。 */
    public static final Map<String, String> DEFAULTS = Map.of(
            "ui.background.enabled", "false",
            "ui.background.brightness", "0.48",
            "ui.background.blur", "0",
            "ui.background.veil", "0.62",
            "ui.background.vignette", "true",
            "ui.background.version", "0");

    private final AppSettingRepository repository;

    public SettingService(AppSettingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Map<String, String> getAll() {
        Map<String, String> result = new LinkedHashMap<>(DEFAULTS);
        repository.findAll().forEach(item -> result.put(item.getSettingKey(), item.getSettingValue()));
        return result;
    }

    @Transactional(readOnly = true)
    public String get(String key, String fallback) {
        return repository.findById(key)
                .map(AppSetting::getSettingValue)
                .orElseGet(() -> DEFAULTS.getOrDefault(key, fallback));
    }

    @Transactional
    public void put(String key, String value) {
        AppSetting setting = repository.findById(key).orElseGet(() -> new AppSetting(key, value));
        setting.setSettingValue(value);
        repository.save(setting);
    }

    @Transactional
    public void putAll(Map<String, String> values) {
        values.forEach(this::put);
    }

    @Transactional
    public void remove(String key) {
        repository.deleteById(key);
    }
}
