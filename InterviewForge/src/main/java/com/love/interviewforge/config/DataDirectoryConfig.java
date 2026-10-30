package com.love.interviewforge.config;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 在所有 Bean（尤其是 DataSource）创建之前准备数据目录。
 *
 * <p>SQLite 只会创建数据库文件，不会创建父目录，所以必须抢在 DataSource 初始化前建好。</p>
 */
@Configuration(proxyBeanMethods = false)
public class DataDirectoryConfig {

    private static final Logger log = LoggerFactory.getLogger(DataDirectoryConfig.class);

    @Bean
    public static BeanFactoryPostProcessor dataDirectoryPreparer() {
        return beanFactory -> {
            Path dir = AppPaths.prepare();
            log.info("数据目录就绪: {}", dir);
            log.info("  ├─ 数据库: {}", dir.resolve("interviewforge.db"));
            log.info("  ├─ 背景图: {}", dir.resolve("assets"));
            log.info("  └─ 导出: {}", dir.resolve("exports"));
        };
    }
}
