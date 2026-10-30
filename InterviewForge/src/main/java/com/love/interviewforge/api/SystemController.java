package com.love.interviewforge.api;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.love.interviewforge.common.ApiResponse;
import com.love.interviewforge.config.AppPaths;

/**
 * 系统自检：表清单、数据目录、库文件大小。供验收与排障使用，只读。
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final JdbcTemplate jdbcTemplate;

    public SystemController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
                String.class);

        Path dataDir = AppPaths.resolve();
        long size = 0L;
        // WAL 模式下最新写入在 -wal 文件里，只报主文件会严重偏小
        for (String name : new String[] {"interviewforge.db", "interviewforge.db-wal", "interviewforge.db-shm"}) {
            Path file = dataDir.resolve(name);
            try {
                if (Files.exists(file)) {
                    size += Files.size(file);
                }
            } catch (Exception ignored) {
                // 读不到大小不影响自检结论
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tables", tables);
        body.put("tableCount", tables.size());
        body.put("dataDir", dataDir.toString());
        body.put("dbSizeBytes", size);
        return ApiResponse.ok(body);
    }
}
