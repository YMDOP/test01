package com.love.interviewforge;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.love.interviewforge.config.AppPaths;

@SpringBootApplication
public class InterviewForgeApplication {

    private static final Logger log = LoggerFactory.getLogger(InterviewForgeApplication.class);

    public static void main(String[] args) {
        // 必须先于任何 Bean 创建：SQLite 不会自动创建父目录
        Path dataDir = AppPaths.prepare();
        log.info("InterviewForge 启动，数据目录 = {}", dataDir);

        SpringApplication.run(InterviewForgeApplication.class, args);
    }

}
