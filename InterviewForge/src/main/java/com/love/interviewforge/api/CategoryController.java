package com.love.interviewforge.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.love.interviewforge.common.ApiResponse;

/**
 * 两级分类树，附带每个节点的题量，供题库页左栏使用。
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final JdbcTemplate jdbcTemplate;

    public CategoryController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/tree")
    public ApiResponse<List<Map<String, Object>>> tree() {
        List<Map<String, Object>> parents = jdbcTemplate.queryForList(
                "SELECT id, name FROM category WHERE parent_id IS NULL ORDER BY id");

        List<Map<String, Object>> children = jdbcTemplate.queryForList("""
                SELECT c.id, c.name, c.parent_id,
                       (SELECT COUNT(*) FROM question q WHERE q.category_id = c.id) AS question_count
                  FROM category c
                 WHERE c.parent_id IS NOT NULL
                 ORDER BY c.id
                """);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> parent : parents) {
            long parentId = ((Number) parent.get("id")).longValue();
            List<Map<String, Object>> nodeChildren = new ArrayList<>();
            long parentQuestions = 0;
            for (Map<String, Object> child : children) {
                if (((Number) child.get("parent_id")).longValue() != parentId) {
                    continue;
                }
                long count = ((Number) child.get("question_count")).longValue();
                parentQuestions += count;
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("id", ((Number) child.get("id")).longValue());
                node.put("name", child.get("name"));
                node.put("questionCount", count);
                nodeChildren.add(node);
            }
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", parentId);
            node.put("name", parent.get("name"));
            node.put("questionCount", parentQuestions);
            node.put("children", nodeChildren);
            result.add(node);
        }
        return ApiResponse.ok(result);
    }
}
