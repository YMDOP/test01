package com.love.interviewforge.ingest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Markdown 解析：按 # 层级维护标题路径，按空行切段落。
 */
public class MarkdownParser {

    public List<ParsedBlock> parse(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<ParsedBlock> blocks = new ArrayList<>();

        // 标题栈：level -> title
        Deque<String>[] stack = new Deque[7];
        for (int i = 1; i <= 6; i++) {
            stack[i] = new ArrayDeque<>();
        }

        StringBuilder paragraph = new StringBuilder();
        StringBuilder codeBlock = new StringBuilder();
        boolean inCode = false;

        for (String raw : lines) {
            String line = raw.stripTrailing();

            if (line.startsWith("```")) {
                if (inCode) {
                    inCode = false;
                    appendBlock(blocks, headingPath(stack), codeBlock.toString());
                    codeBlock.setLength(0);
                } else {
                    flushParagraph(blocks, stack, paragraph);
                    inCode = true;
                }
                continue;
            }
            if (inCode) {
                codeBlock.append(line).append('\n');
                continue;
            }

            if (line.isBlank()) {
                flushParagraph(blocks, stack, paragraph);
                continue;
            }

            int level = headingLevel(line);
            if (level > 0) {
                flushParagraph(blocks, stack, paragraph);
                for (int i = level; i <= 6; i++) {
                    stack[i].clear();
                }
                stack[level].clear();
                stack[level].push(stripHeading(line));
                continue;
            }

            // 目录那种纯链接行、分隔线不参与切片
            if (line.startsWith("---") || line.matches("^\\s*[-*]\\s*\\[.*\\]\\(#.*\\)\\s*$")) {
                continue;
            }
            paragraph.append(line).append('\n');
        }

        flushParagraph(blocks, stack, paragraph);
        if (codeBlock.length() > 0) {
            appendBlock(blocks, headingPath(stack), codeBlock.toString());
        }
        return blocks;
    }

    private void flushParagraph(List<ParsedBlock> blocks, Deque<String>[] stack, StringBuilder paragraph) {
        String text = paragraph.toString().trim();
        paragraph.setLength(0);
        if (text.isEmpty()) {
            return;
        }
        appendBlock(blocks, headingPath(stack), text);
    }

    private void appendBlock(List<ParsedBlock> blocks, String heading, String text) {
        String trimmed = text.trim();
        if (trimmed.length() < 20) {
            return;
        }
        // 代码块超过 1200 字会稀释语义，截断
        if (trimmed.length() > 1200) {
            trimmed = trimmed.substring(0, 1200);
        }
        blocks.add(new ParsedBlock(heading, trimmed));
    }

    private String headingPath(Deque<String>[] stack) {
        List<String> parts = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            if (!stack[i].isEmpty()) {
                parts.add(stack[i].peek());
            }
        }
        return String.join(" > ", parts);
    }

    private int headingLevel(String line) {
        int level = 0;
        while (level < line.length() && line.charAt(level) == '#' && level < 6) {
            level++;
        }
        if (level == 0) {
            return 0;
        }
        return level < line.length() && line.charAt(level) == ' ' ? level : 0;
    }

    private String stripHeading(String line) {
        return line.replaceFirst("^#{1,6}\\s*", "").trim();
    }
}
