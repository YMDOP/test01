package com.love.interviewforge.config;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 轻量结构升级：schema.sql 用 IF NOT EXISTS，老库不会自动加列，
 * 这里按需 ALTER，保证已有数据库也能升级。
 */
@Component
@Order(1)
public class SchemaUpgrader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaUpgrader.class);

    private final JdbcTemplate jdbcTemplate;

    public SchemaUpgrader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureColumn("category", "parent_id", "INTEGER REFERENCES category (id) ON DELETE CASCADE");
    }

    private void ensureColumn(String table, String column, String definition) {
        Set<String> columns = new HashSet<>();
        jdbcTemplate.query("PRAGMA table_info(" + table + ")",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> columns.add(rs.getString("name")));
        if (columns.contains(column)) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        log.info("已升级表结构: {}.{}", table, column);
    }
}
