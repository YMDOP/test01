package com.love.interviewforge.api;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.love.interviewforge.common.ApiResponse;

/**
 * 健康检查：前端与部署脚本都靠它判断服务是否可用。
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final Environment environment;

    public HealthController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("application", environment.getProperty("spring.application.name", "InterviewForge"));
        body.put("version", environment.getProperty("app.version", "0.0.1"));
        body.put("dataDir", environment.getProperty("app.data-dir", "./data"));
        body.put("javaVersion", System.getProperty("java.version"));
        body.put("time", OffsetDateTime.now().toString());
        return ApiResponse.ok(body);
    }
}
