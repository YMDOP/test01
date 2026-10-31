package com.love.interviewforge.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.love.interviewforge.domain.Category;
import com.love.interviewforge.domain.CategoryRepository;

/**
 * 统一抽题入口。练习与后续的模拟面试都必须走这里。
 */
@Service
public class PickService {

    private static final RowMapper<QuestionService.QuestionView> ROW_MAPPER = (rs, rowNum) ->
            new QuestionService.QuestionView(
                    rs.getLong("id"),
                    rs.getLong("category_id"),
                    rs.getString("category_name"),
                    rs.getString("parent_name"),
                    rs.getString("type"),
                    rs.getInt("difficulty"),
                    rs.getString("stem"),
                    rs.getString("options_json"),
                    rs.getString("standard_answer"),
                    rs.getString("explanation"),
                    rs.getLong("source_chunk_id"),
                    rs.getString("quality_flag"),
                    rs.getString("mastery_status"));

    private final JdbcTemplate jdbcTemplate;
    private final CategoryRepository categoryRepository;

    public PickService(JdbcTemplate jdbcTemplate, CategoryRepository categoryRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.categoryRepository = categoryRepository;
    }

    public record PickRequest(List<Long> categoryIds,
                              List<String> types,
                              List<String> mastery,
                              Integer count,
                              String order,
                              Integer excludeRecentDays,
                              Boolean includeAnswer) {
    }

    public record PickResult(List<QuestionService.QuestionView> items, boolean insufficient,
                             int matched, int requested) {
    }

    @Transactional(readOnly = true)
    public PickResult pick(PickRequest request) {
        int requested = request.count() == null || request.count() <= 0 ? 10 : Math.min(request.count(), 100);
        List<Object> params = new ArrayList<>();
        String where = buildWhere(request, params);

        String base = """
                FROM question q
                JOIN category c ON c.id = q.category_id
                JOIN category p ON p.id = c.parent_id
                LEFT JOIN mastery m ON m.question_id = q.id
                """;

        Long matched = jdbcTemplate.queryForObject("SELECT COUNT(*) " + base + where, Long.class, params.toArray());
        int matchedCount = matched == null ? 0 : matched.intValue();
        if (matchedCount == 0) {
            return new PickResult(List.of(), true, 0, requested);
        }

        List<Object> listParams = new ArrayList<>(params);
        listParams.add(requested);
        String order = "OLDEST".equalsIgnoreCase(request.order()) ? "q.id ASC" : "RANDOM()";
        List<QuestionService.QuestionView> items = jdbcTemplate.query("""
                SELECT q.id, q.category_id, c.name AS category_name, p.name AS parent_name,
                       q.type, q.difficulty, q.stem, q.options_json, q.standard_answer, q.explanation,
                       q.source_chunk_id, q.quality_flag,
                       IFNULL(m.status, 'UNTRIED') AS mastery_status
                """ + base + where + " ORDER BY " + order + " LIMIT ?", ROW_MAPPER, listParams.toArray());

        boolean includeAnswer = Boolean.TRUE.equals(request.includeAnswer());
        if (!includeAnswer) {
            items = items.stream().map(item -> new QuestionService.QuestionView(
                    item.id(), item.categoryId(), item.categoryName(), item.parentName(), item.type(),
                    item.difficulty(), item.stem(), item.optionsJson(), null, null,
                    item.sourceChunkId(), item.qualityFlag(), item.masteryStatus())).toList();
        }

        return new PickResult(items, items.size() < requested, matchedCount, requested);
    }

    private String buildWhere(PickRequest request, List<Object> params) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");

        List<Long> categoryIds = expandCategories(request.categoryIds());
        if (!categoryIds.isEmpty()) {
            where.append(" AND q.category_id IN (").append(placeholders(categoryIds.size())).append(")");
            params.addAll(categoryIds);
        }
        if (request.types() != null && !request.types().isEmpty()) {
            where.append(" AND q.type IN (").append(placeholders(request.types().size())).append(")");
            params.addAll(request.types());
        }
        if (request.mastery() != null && !request.mastery().isEmpty()) {
            where.append(" AND IFNULL(m.status, 'UNTRIED') IN (")
                    .append(placeholders(request.mastery().size())).append(")");
            params.addAll(request.mastery());
        }
        if (request.excludeRecentDays() != null && request.excludeRecentDays() > 0) {
            String since = Instant.now().minus(request.excludeRecentDays(), ChronoUnit.DAYS).toString();
            where.append(" AND NOT EXISTS (SELECT 1 FROM practice_item pi "
                    + "WHERE pi.question_id = q.id AND pi.answered_at >= ?)");
            params.add(since);
        }
        return where.toString();
    }

    /** 传一级分类时自动展开为它下面的二级分类。 */
    private List<Long> expandCategories(List<Long> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        List<Long> result = new ArrayList<>(input);
        for (Long id : input) {
            Category category = categoryRepository.findById(id).orElse(null);
            if (category != null && category.parentId == null) {
                for (Category child : categoryRepository.findByParentIdIsNotNullOrderByIdAsc()) {
                    if (category.id.equals(child.parentId) && !result.contains(child.id)) {
                        result.add(child.id);
                    }
                }
            }
        }
        return result.isEmpty() ? Collections.emptyList() : result;
    }

    /** 按 id 批量取题（用于恢复练习会话），一次查询完成。 */
    @Transactional(readOnly = true)
    public List<QuestionService.QuestionView> pickByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        String in = placeholders(ids.size());
        List<QuestionService.QuestionView> rows = jdbcTemplate.query("""
                SELECT q.id, q.category_id, c.name AS category_name, p.name AS parent_name,
                       q.type, q.difficulty, q.stem, q.options_json, q.standard_answer, q.explanation,
                       q.source_chunk_id, q.quality_flag,
                       IFNULL(m.status, 'UNTRIED') AS mastery_status
                  FROM question q
                  JOIN category c ON c.id = q.category_id
                  JOIN category p ON p.id = c.parent_id
                  LEFT JOIN mastery m ON m.question_id = q.id
                 WHERE q.id IN (""" + in + ")", ROW_MAPPER, ids.toArray());

        // 按传入顺序返回
        List<QuestionService.QuestionView> ordered = new ArrayList<>();
        for (Long id : ids) {
            rows.stream().filter(row -> id.equals(row.id())).findFirst().ifPresent(ordered::add);
        }
        return ordered;
    }

    private String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }
}
