package com.love.interviewforge.service;

import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.love.interviewforge.common.BusinessException;
import com.love.interviewforge.domain.WrongBook;
import com.love.interviewforge.domain.WrongBookRepository;

/** 错题本读写。 */
@Service
public class WrongBookService {

    private static final RowMapper<WrongItem> ROW_MAPPER = (rs, rowNum) -> new WrongItem(
            rs.getLong("id"),
            rs.getLong("question_id"),
            rs.getString("type"),
            rs.getString("stem"),
            rs.getString("category_name"),
            rs.getString("parent_name"),
            rs.getString("source_type"),
            rs.getString("created_at"),
            rs.getString("resolved_at"));

    private final JdbcTemplate jdbcTemplate;
    private final WrongBookRepository repository;

    public WrongBookService(JdbcTemplate jdbcTemplate, WrongBookRepository repository) {
        this.jdbcTemplate = jdbcTemplate;
        this.repository = repository;
    }

    public record WrongItem(Long id, Long questionId, String type, String stem, String categoryName,
                            String parentName, String sourceType, String createdAt, String resolvedAt) {
    }

    @Transactional(readOnly = true)
    public List<WrongItem> list(boolean includeResolved, String type) {
        StringBuilder sql = new StringBuilder("""
                SELECT w.id, w.question_id, q.type, q.stem, c.name AS category_name,
                       p.name AS parent_name, w.source_type, w.created_at, w.resolved_at
                  FROM wrong_book w
                  JOIN question q ON q.id = w.question_id
                  JOIN category c ON c.id = q.category_id
                  JOIN category p ON p.id = c.parent_id
                 WHERE 1 = 1
                """);
        java.util.List<Object> params = new java.util.ArrayList<>();
        if (!includeResolved) {
            sql.append(" AND w.resolved_at IS NULL");
        }
        if (type != null && !type.isBlank()) {
            sql.append(" AND q.type = ?");
            params.add(type);
        }
        sql.append(" ORDER BY w.id DESC");
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    @Transactional
    public void resolve(Long id) {
        WrongBook entry = repository.findById(id)
                .orElseThrow(() -> new BusinessException(2001, "错题记录不存在: " + id));
        entry.resolvedAt = Instant.now().toString();
        repository.save(entry);
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new BusinessException(2001, "错题记录不存在: " + id);
        }
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public long unresolvedCount() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wrong_book WHERE resolved_at IS NULL", Long.class);
        return count == null ? 0 : count;
    }
}
