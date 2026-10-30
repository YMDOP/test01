package com.love.interviewforge.ingest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

/**
 * docx 解析：标题样式维护层级，段落与表格单元格转文本。
 */
public class DocxParser {

    public List<ParsedBlock> parse(Path file) throws IOException {
        List<ParsedBlock> blocks = new ArrayList<>();
        try (InputStream in = Files.newInputStream(file);
             XWPFDocument document = new XWPFDocument(in)) {

            Deque<String>[] stack = new Deque[7];
            for (int i = 1; i <= 6; i++) {
                stack[i] = new ArrayDeque<>();
            }

            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String text = paragraph.getText();
                    if (text == null || text.isBlank()) {
                        continue;
                    }
                    int level = headingLevel(paragraph);
                    if (level > 0) {
                        for (int i = level; i <= 6; i++) {
                            stack[i].clear();
                        }
                        stack[level].clear();
                        stack[level].push(text.trim());
                        continue;
                    }
                    blocks.add(new ParsedBlock(headingPath(stack), text.trim()));
                } else if (element instanceof XWPFTable table) {
                    String tableText = tableToText(table);
                    if (tableText.length() > 20) {
                        blocks.add(new ParsedBlock(headingPath(stack), tableText));
                    }
                }
            }
        }
        return blocks;
    }

    private String tableToText(XWPFTable table) {
        StringBuilder sb = new StringBuilder();
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                String cellText = cell.getText();
                if (cellText != null && !cellText.isBlank()) {
                    cells.add(cellText.replaceAll("\\s+", " ").trim());
                }
            }
            if (!cells.isEmpty()) {
                sb.append(String.join(" | ", cells)).append('\n');
            }
        }
        return sb.toString().trim();
    }

    private int headingLevel(XWPFParagraph paragraph) {
        String style = paragraph.getStyle();
        if (style == null) {
            return 0;
        }
        String lower = style.toLowerCase();
        if (!lower.startsWith("heading") && !lower.startsWith("标题")) {
            return 0;
        }
        for (char c : style.toCharArray()) {
            if (Character.isDigit(c)) {
                int level = c - '0';
                return Math.min(Math.max(level, 1), 6);
            }
        }
        return 1;
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
}
