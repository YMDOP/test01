package com.love.interviewforge.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.love.interviewforge.common.BusinessException;
import com.love.interviewforge.domain.Category;
import com.love.interviewforge.domain.CategoryRepository;
import com.love.interviewforge.domain.Question;
import com.love.interviewforge.domain.QuestionRepository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 题库读写。列表查询用一条 join SQL，避免 N+1。
 */
@Service
public class QuestionService {

    private static final RowMapper<QuestionView> ROW_MAPPER = (rs, rowNum) -> new QuestionView(
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
    private final QuestionRepository questionRepository;
    private final CategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;

    public QuestionService(JdbcTemplate jdbcTemplate,
                           QuestionRepository questionRepository,
                           CategoryRepository categoryRepository,
                           ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.questionRepository = questionRepository;
        this.categoryRepository = categoryRepository;
        this.objectMapper = objectMapper;
    }

    public record QuestionView(Long id, Long categoryId, String categoryName, String parentName,
                               String type, int difficulty, String stem, String optionsJson,
                               String standardAnswer, String explanation, Long sourceChunkId,
                               String qualityFlag, String masteryStatus) {
    }

    public record PageResult(List<QuestionView> items, long total) {
    }

    public record QuestionInput(Long categoryId, String type, Integer difficulty, String stem,
                                List<String> options, String standardAnswer, String explanation,
                                Long sourceChunkId) {
    }

    public PageResult list(List<Long> categoryIds, String type, String mastery, String keyword,
                           int page, int size) {
        List<Object> params = new ArrayList<>();
        String where = buildWhere(categoryIds, type, mastery, keyword, params);

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM question q "
                + "JOIN category c ON c.id = q.category_id "
                + "JOIN category p ON p.id = c.parent_id "
                + "LEFT JOIN mastery m ON m.question_id = q.id " + where, Long.class, params.toArray());

        List<Object> listParams = new ArrayList<>(params);
        listParams.add(size);
        listParams.add((page - 1) * size);
        List<QuestionView> items = jdbcTemplate.query("""
                SELECT q.id, q.category_id, c.name AS category_name, p.name AS parent_name,
                       q.type, q.difficulty, q.stem, q.options_json, q.standard_answer, q.explanation,
                       q.source_chunk_id, q.quality_flag,
                       IFNULL(m.status, 'UNTRIED') AS mastery_status
                  FROM question q
                  JOIN category c ON c.id = q.category_id
                  JOIN category p ON p.id = c.parent_id
                  LEFT JOIN mastery m ON m.question_id = q.id
                """ + where + " ORDER BY q.id DESC LIMIT ? OFFSET ?", ROW_MAPPER, listParams.toArray());

        return new PageResult(items, total == null ? 0 : total);
    }

    private String buildWhere(List<Long> categoryIds, String type, String mastery, String keyword,
                              List<Object> params) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        if (categoryIds != null && !categoryIds.isEmpty()) {
            where.append(" AND q.category_id IN (").append(placeholders(categoryIds.size())).append(")");
            params.addAll(categoryIds);
        }
        if (type != null && !type.isBlank()) {
            where.append(" AND q.type = ?");
            params.add(type);
        }
        if (mastery != null && !mastery.isBlank()) {
            where.append(" AND IFNULL(m.status, 'UNTRIED') = ?");
            params.add(mastery);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (q.stem LIKE ? OR q.explanation LIKE ?)");
            params.add("%" + keyword.trim() + "%");
            params.add("%" + keyword.trim() + "%");
        }
        return where.toString();
    }

    private String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    public QuestionView get(Long id) {
        List<QuestionView> rows = jdbcTemplate.query("""
                SELECT q.id, q.category_id, c.name AS category_name, p.name AS parent_name,
                       q.type, q.difficulty, q.stem, q.options_json, q.standard_answer, q.explanation,
                       q.source_chunk_id, q.quality_flag,
                       IFNULL(m.status, 'UNTRIED') AS mastery_status
                  FROM question q
                  JOIN category c ON c.id = q.category_id
                  JOIN category p ON p.id = c.parent_id
                  LEFT JOIN mastery m ON m.question_id = q.id
                 WHERE q.id = ?
                """, ROW_MAPPER, id);
        if (rows.isEmpty()) {
            throw new BusinessException(2001, "题目不存在: " + id);
        }
        return rows.get(0);
    }

    @Transactional
    public QuestionView create(QuestionInput input) {
        Question question = new Question();
        apply(question, input);
        question.origin = "MANUAL";
        question.qualityFlag = "OK";
        question.createdAt = Instant.now().toString();
        question.updatedAt = question.createdAt;
        Question saved = questionRepository.saveAndFlush(question);
        return get(saved.id);
    }

    @Transactional
    public QuestionView update(Long id, QuestionInput input) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(2001, "题目不存在: " + id));
        apply(question, input);
        question.updatedAt = Instant.now().toString();
        // 必须 flush：紧接着的 get() 走 JdbcTemplate 另一条连接，否则读到旧值
        questionRepository.saveAndFlush(question);
        return get(id);
    }

    private void apply(Question question, QuestionInput input) {
        if (input.categoryId() == null) {
            throw new BusinessException(1002, "必须选择所属分类");
        }
        Category category = categoryRepository.findById(input.categoryId())
                .orElseThrow(() -> new BusinessException(1002, "分类不存在"));

        String type = input.type() == null ? "" : input.type().trim().toUpperCase();
        if (!List.of("SA", "MC", "TF").contains(type)) {
            throw new BusinessException(1002, "题型只能是 SA / MC / TF");
        }
        if (isBlank(input.stem()) || isBlank(input.standardAnswer()) || isBlank(input.explanation())) {
            throw new BusinessException(1002, "题干、标准答案、解析都是必填");
        }
        if (input.sourceChunkId() == null) {
            throw new BusinessException(1002, "必须绑定来源切片");
        }
        Long chunkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chunk WHERE id = ?", Long.class, input.sourceChunkId());
        if (chunkCount == null || chunkCount == 0) {
            throw new BusinessException(1002, "来源切片不存在: " + input.sourceChunkId());
        }

        String optionsJson = null;
        String answer = input.standardAnswer().trim();
        if ("MC".equals(type)) {
            if (input.options() == null || input.options().size() < 2) {
                throw new BusinessException(1002, "选择题至少需要 2 个选项");
            }
            try {
                optionsJson = objectMapper.writeValueAsString(input.options());
            } catch (RuntimeException e) {
                throw new BusinessException(1002, "选项序列化失败");
            }
        }
        if ("TF".equals(type)) {
            String upper = answer.toUpperCase();
            if (!"TRUE".equals(upper) && !"FALSE".equals(upper)) {
                throw new BusinessException(1002, "判断题答案只能是 TRUE 或 FALSE");
            }
            answer = upper;
        }

        question.categoryId = category.id;
        question.type = type;
        question.difficulty = input.difficulty() == null ? 3 : Math.min(Math.max(input.difficulty(), 1), 5);
        question.stem = input.stem().trim();
        question.optionsJson = optionsJson;
        question.standardAnswer = answer;
        question.explanation = input.explanation().trim();
        question.sourceChunkId = input.sourceChunkId();
    }

    @Transactional
    public void delete(Long id) {
        if (!questionRepository.existsById(id)) {
            throw new BusinessException(2001, "题目不存在: " + id);
        }
        deleteRelated(List.of(id));
        questionRepository.deleteById(id);
    }

    @Transactional
    public int batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        deleteRelated(ids);
        String in = placeholders(ids.size());
        return jdbcTemplate.update("DELETE FROM question WHERE id IN (" + in + ")", ids.toArray());
    }

    /** 删题前显式清理关联表，即使外键级联没生效也不会留脏数据。 */
    private void deleteRelated(List<Long> ids) {
        String in = placeholders(ids.size());
        Object[] args = ids.toArray();
        jdbcTemplate.update("DELETE FROM mastery WHERE question_id IN (" + in + ")", args);
        jdbcTemplate.update("DELETE FROM wrong_book WHERE question_id IN (" + in + ")", args);
        jdbcTemplate.update("DELETE FROM practice_item WHERE question_id IN (" + in + ")", args);
        jdbcTemplate.update("DELETE FROM exam_item WHERE question_id IN (" + in + ")", args);
    }

    public List<String> parseOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(optionsJson, new TypeReference<List<String>>() {
            });
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
