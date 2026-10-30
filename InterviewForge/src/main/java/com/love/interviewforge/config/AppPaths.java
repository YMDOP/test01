package com.love.interviewforge.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 运行期路径解析。所有可写数据必须落在 data 目录下。
 *
 * <p>优先级：环境变量 INTERVIEWFORGE_DATA_DIR → 系统属性 app.data-dir → ./data</p>
 */
public final class AppPaths {

    public static final String DATA_DIR_PROPERTY = "app.data-dir";
    public static final String DATA_DIR_ENV = "INTERVIEWFORGE_DATA_DIR";

    /** data 下的标准子目录，启动时确保存在。 */
    private static final String[] SUB_DIRS = {"assets", "exports", "logs", "backup"};

    private AppPaths() {
    }

    public static Path resolve() {
        String raw = System.getenv(DATA_DIR_ENV);
        if (raw == null || raw.isBlank()) {
            raw = System.getProperty(DATA_DIR_PROPERTY);
        }
        if (raw == null || raw.isBlank()) {
            raw = "./data";
        }
        return Paths.get(raw).toAbsolutePath().normalize();
    }

    /**
     * 创建数据目录及子目录，并把解析后的绝对路径写回系统属性，
     * 保证 application.yaml 中的 ${app.data-dir} 拿到的是同一个位置。
     */
    public static Path prepare() {
        Path dir = resolve();
        try {
            Files.createDirectories(dir);
            for (String sub : SUB_DIRS) {
                Files.createDirectories(dir.resolve(sub));
            }
        } catch (IOException e) {
            throw new IllegalStateException("无法创建数据目录: " + dir + "，请检查磁盘权限", e);
        }
        System.setProperty(DATA_DIR_PROPERTY, dir.toString());
        return dir;
    }

    public static Path sub(String name) {
        return resolve().resolve(name);
    }
}
