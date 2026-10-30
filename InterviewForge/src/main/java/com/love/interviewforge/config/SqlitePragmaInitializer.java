package com.love.interviewforge.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * SQLite 连接级调优：WAL 让读写不互相阻塞，busy_timeout 避免瞬时锁冲突直接报错。
 * foreign_keys 是连接级开关，由 Hikari 的 connection-init-sql 负责。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SqlitePragmaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SqlitePragmaInitializer.class);

    private final DataSource dataSource;

    public SqlitePragmaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA busy_timeout=5000");
            log.info("SQLite PRAGMA 已应用: journal_mode=WAL, synchronous=NORMAL, busy_timeout=5000");
        } catch (SQLException e) {
            // 不阻断启动：只降级提示，后续写入会变慢但可用
            log.warn("应用 SQLite PRAGMA 失败，数据库仍可运行: {}", e.getMessage());
        }
    }
}
