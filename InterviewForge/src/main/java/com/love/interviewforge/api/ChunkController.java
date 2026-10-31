package com.love.interviewforge.api;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.love.interviewforge.common.ApiResponse;

/**
 * 切片检索：手工建题时必须绑定一个来源切片，这里提供按关键词查找。
 */
@RestController
@RequestMapping("/api/chunks")
public class ChunkController {

    private final JdbcTemplate jdbcTemplate;

    public ChunkController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "10") int limit) {
        int safeLimit = Math.min(Math.max(1, limit), 30);
        String like = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, heading_path, substr(text, 1, 120) AS snippet
                  FROM chunk
                 WHERE text LIKE ? OR IFNULL(heading_path, '') LIKE ?
                 ORDER BY id
                 LIMIT ?
                """, like, like, safeLimit);
        return ApiResponse.ok(rows);
    }
}
