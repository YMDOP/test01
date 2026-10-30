package com.love.interviewforge.ingest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 目录扫描：递归找出 md/docx/pdf，跳过隐藏目录与常见噪声目录。
 */
public class DirectoryScanner {

    private static final Set<String> EXTENSIONS = Set.of("md", "docx", "pdf");
    private static final Set<String> SKIP_DIRS = Set.of(
            ".obsidian", ".git", "node_modules", "target", ".idea", ".trash", "$RECYCLE.BIN");

    public record ScanResult(Path root, List<Path> files) {
    }

    public ScanResult scan(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("目录不存在: " + root);
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root, 12)) {
            stream.filter(Files::isRegularFile)
                    .filter(this::isSupported)
                    .filter(path -> !hasSkippedSegment(root.relativize(path)))
                    .forEach(files::add);
        }
        files.sort(Comparator.comparing(Path::toString));
        return new ScanResult(root, files);
    }

    public boolean isSupported(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot > 0 && EXTENSIONS.contains(name.substring(dot + 1));
    }

    private boolean hasSkippedSegment(Path relative) {
        for (Path part : relative) {
            if (SKIP_DIRS.contains(part.toString().toLowerCase(Locale.ROOT)) || part.toString().startsWith(".")) {
                return true;
            }
        }
        return false;
    }
}
