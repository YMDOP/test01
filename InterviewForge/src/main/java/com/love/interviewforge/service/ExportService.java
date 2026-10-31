package com.love.interviewforge.service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.love.interviewforge.common.BusinessException;
import com.love.interviewforge.config.AppPaths;

/**
 * 题库导出：Markdown（按章节）与 Excel（多 sheet）。
 */
@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static final String TYPE_LABEL_SA = "简答";
    private static final String TYPE_LABEL_MC = "选择";
    private static final String TYPE_LABEL_TF = "判断";

    private final JdbcTemplate jdbcTemplate;

    public ExportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record ExportResult(Long id, String format, String path, long sizeBytes, int questionCount) {
    }

    public record ExportRequest(String format, Long categoryId, Boolean includeAnswer,
                                Boolean includeExplanation) {
    }

    private record QuestionRow(long id, String parent, String child, String type, int difficulty, String stem,
                       String optionsJson, String answer, String explanation, Long chunkId,
                       String heading, String mastery) {
    }

    @Transactional
    public ExportResult export(ExportRequest request) {
        String format = request != null && request.format() != null
                ? request.format().trim().toUpperCase() : "MD";
        if (!List.of("MD", "XLSX").contains(format)) {
            throw new BusinessException(1002, "导出格式只能是 MD 或 XLSX");
        }
        boolean includeAnswer = request == null || request.includeAnswer() == null
                || Boolean.TRUE.equals(request.includeAnswer());
        boolean includeExplanation = request == null || request.includeExplanation() == null
                || Boolean.TRUE.equals(request.includeExplanation());
        Long categoryId = request == null ? null : request.categoryId();

        List<QuestionRow> rows = loadRows(categoryId);
        if (rows.isEmpty()) {
            throw new BusinessException(2001, "没有可导出的题目");
        }

        String stamp = LocalDateTime.now(ZONE).format(FILE_STAMP);
        String scope = categoryId == null ? "all" : "category-" + categoryId;
        String extension = "MD".equals(format) ? "md" : "xlsx";
        Path target = AppPaths.sub("exports").resolve(stamp + "-" + scope + "." + extension);

        try {
            Files.createDirectories(target.getParent());
            if ("MD".equals(format)) {
                Files.writeString(target, buildMarkdown(rows, includeAnswer, includeExplanation),
                        StandardCharsets.UTF_8);
            } else {
                try (Workbook workbook = buildExcel(rows, includeAnswer, includeExplanation);
                     OutputStream out = Files.newOutputStream(target)) {
                    workbook.write(out);
                }
            }
        } catch (IOException e) {
            throw new BusinessException(5000, "导出失败：" + e.getMessage(), e);
        }

        long size;
        try {
            size = Files.size(target);
        } catch (IOException e) {
            size = 0;
        }

        jdbcTemplate.update("INSERT INTO export_job (format, scope, path, created_at) VALUES (?, ?, ?, ?)",
                format, scope, target.toString(), Instant.now().toString());
        Long id = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);

        log.info("导出完成: {} -> {} ({} 题, {} 字节)", format, target, rows.size(), size);
        return new ExportResult(id, format, target.toString(), size, rows.size());
    }

    public record FilePayload(String filename, String contentType, byte[] bytes) {
    }

    @Transactional(readOnly = true)
    public FilePayload loadFile(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT format, path FROM export_job WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw new BusinessException(2001, "导出记录不存在: " + id);
        }
        Map<String, Object> row = rows.get(0);
        String format = String.valueOf(row.get("format"));
        Path path = Path.of(String.valueOf(row.get("path"))).toAbsolutePath().normalize();

        Path exportsDir = AppPaths.sub("exports").toAbsolutePath().normalize();
        if (!path.startsWith(exportsDir)) {
            throw new BusinessException(2001, "导出文件路径非法");
        }
        if (!Files.exists(path)) {
            throw new BusinessException(2001, "导出文件已不存在，请重新导出");
        }
        try {
            String contentType = "MD".equals(format)
                    ? "text/markdown; charset=utf-8"
                    : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            return new FilePayload(path.getFileName().toString(), contentType, Files.readAllBytes(path));
        } catch (IOException e) {
            throw new BusinessException(5000, "读取导出文件失败：" + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> history() {
        return jdbcTemplate.queryForList("""
                SELECT id, format, scope, path, created_at
                  FROM export_job
                 ORDER BY id DESC
                 LIMIT 20
                """);
    }

    private List<QuestionRow> loadRows(Long categoryId) {
        StringBuilder sql = new StringBuilder("""
                SELECT q.id, p.name AS parent_name, c.name AS child_name, q.type, q.difficulty,
                       q.stem, q.options_json, q.standard_answer, q.explanation,
                       q.source_chunk_id, ch.heading_path,
                       IFNULL(m.status, 'UNTRIED') AS mastery
                  FROM question q
                  JOIN category c ON c.id = q.category_id
                  JOIN category p ON p.id = c.parent_id
                  LEFT JOIN chunk ch ON ch.id = q.source_chunk_id
                  LEFT JOIN mastery m ON m.question_id = q.id
                """);
        List<Object> params = new ArrayList<>();
        if (categoryId != null) {
            sql.append(" WHERE c.id = ? OR c.parent_id = ?");
            params.add(categoryId);
            params.add(categoryId);
        }
        sql.append(" ORDER BY p.id, c.id, q.id");

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new QuestionRow(
                rs.getLong("id"),
                rs.getString("parent_name"),
                rs.getString("child_name"),
                rs.getString("type"),
                rs.getInt("difficulty"),
                rs.getString("stem"),
                rs.getString("options_json"),
                rs.getString("standard_answer"),
                rs.getString("explanation"),
                rs.getLong("source_chunk_id"),
                rs.getString("heading_path"),
                rs.getString("mastery")), params.toArray());
    }

    // ------------------------------------------------------------
    // Markdown
    // ------------------------------------------------------------

    private String buildMarkdown(List<QuestionRow> rows, boolean includeAnswer, boolean includeExplanation) {
        StringBuilder sb = new StringBuilder();
        sb.append("# InterviewForge 题库导出\n\n");
        sb.append("> 导出时间：").append(LocalDateTime.now(ZONE)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append("　")
                .append("共 ").append(rows.size()).append(" 题\n\n");
        sb.append("---\n\n");

        String currentParent = null;
        String currentChild = null;
        int index = 0;

        for (QuestionRow row : rows) {
            if (!row.parent().equals(currentParent)) {
                currentParent = row.parent();
                currentChild = null;
                sb.append("## ").append(currentParent).append("\n\n");
            }
            if (!row.child().equals(currentChild)) {
                currentChild = row.child();
                index = 0;
                sb.append("### ").append(currentChild).append("\n\n");
            }
            index++;
            sb.append("**").append(index).append(". [").append(label(row.type()))
                    .append("] ").append(row.stem().replace("\n", " ")).append("**\n\n");

            if ("MC".equals(row.type()) && row.optionsJson() != null) {
                for (String option : splitOptions(row.optionsJson())) {
                    sb.append("- ").append(option.replace("\n", " ")).append('\n');
                }
                sb.append('\n');
            }
            if (includeAnswer) {
                sb.append("> 标准答案：").append(oneLine(row.answer())).append("\n>\n");
            }
            if (includeExplanation) {
                sb.append("> 解析：").append(oneLine(row.explanation())).append("\n>\n");
            }
            sb.append("> 来源：切片 #").append(row.chunkId())
                    .append(row.heading() == null || row.heading().isBlank() ? "" : " · " + row.heading())
                    .append("　掌握度：").append(masteryLabel(row.mastery())).append("\n\n");
        }
        return sb.toString();
    }

    private List<String> splitOptions(String optionsJson) {
        List<String> options = new ArrayList<>();
        if (optionsJson == null || optionsJson.isBlank()) {
            return options;
        }
        String text = optionsJson.trim();
        if (text.startsWith("[") && text.endsWith("]")) {
            text = text.substring(1, text.length() - 1);
        }
        boolean inQuote = false;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
                continue;
            }
            if (c == ',' && !inQuote) {
                options.add(current.toString());
                current.setLength(0);
                continue;
            }
            if (inQuote) {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            options.add(current.toString());
        }
        return options;
    }

    // ------------------------------------------------------------
    // Excel
    // ------------------------------------------------------------

    private Workbook buildExcel(List<QuestionRow> rows, boolean includeAnswer, boolean includeExplanation) {
        Workbook workbook = new XSSFWorkbook();
        CellStyle headerStyle = headerStyle(workbook);

        Sheet questionSheet = workbook.createSheet("题目");
        List<String> headers = new ArrayList<>(List.of("ID", "一级分类", "二级分类", "题型", "难度", "题干"));
        if (rows.stream().anyMatch(row -> "MC".equals(row.type()))) {
            headers.add("选项");
        }
        if (includeAnswer) {
            headers.add("标准答案");
        }
        if (includeExplanation) {
            headers.add("解析");
        }
        headers.add("来源切片");
        headers.add("原文标题");
        headers.add("掌握度");

        writeHeader(questionSheet, headers, headerStyle);
        int rowIndex = 1;
        for (QuestionRow data : rows) {
            Row row = questionSheet.createRow(rowIndex++);
            int col = 0;
            row.createCell(col++).setCellValue(data.id());
            row.createCell(col++).setCellValue(data.parent());
            row.createCell(col++).setCellValue(data.child());
            row.createCell(col++).setCellValue(label(data.type()));
            row.createCell(col++).setCellValue(data.difficulty());
            row.createCell(col++).setCellValue(data.stem());
            if (headers.contains("选项")) {
                row.createCell(col++).setCellValue(data.optionsJson() == null ? "" : data.optionsJson());
            }
            if (includeAnswer) {
                row.createCell(col++).setCellValue(data.answer());
            }
            if (includeExplanation) {
                row.createCell(col++).setCellValue(data.explanation());
            }
            row.createCell(col++).setCellValue(data.chunkId());
            row.createCell(col++).setCellValue(data.heading() == null ? "" : data.heading());
            row.createCell(col).setCellValue(masteryLabel(data.mastery()));
        }
        for (int i = 0; i < headers.size(); i++) {
            questionSheet.setColumnWidth(i, i == 5 ? 60 * 256 : 22 * 256);
        }

        // 按分类统计
        Map<String, long[]> byCategory = new LinkedHashMap<>();
        Map<String, long[]> byType = new LinkedHashMap<>();
        Map<String, long[]> byMastery = new LinkedHashMap<>();
        for (QuestionRow data : rows) {
            String key = data.parent() + " / " + data.child();
            byCategory.computeIfAbsent(key, ignored -> new long[1])[0]++;
            byType.computeIfAbsent(label(data.type()), ignored -> new long[1])[0]++;
            byMastery.computeIfAbsent(masteryLabel(data.mastery()), ignored -> new long[1])[0]++;
        }

        Sheet categorySheet = workbook.createSheet("按分类统计");
        writeHeader(categorySheet, List.of("分类", "题数"), headerStyle);
        int categoryRow = 1;
        for (Map.Entry<String, long[]> entry : byCategory.entrySet()) {
            Row row = categorySheet.createRow(categoryRow++);
            row.createCell(0).setCellValue(entry.getKey());
            row.createCell(1).setCellValue(entry.getValue()[0]);
        }
        categorySheet.setColumnWidth(0, 40 * 256);
        categorySheet.setColumnWidth(1, 10 * 256);

        Sheet typeSheet = workbook.createSheet("按题型统计");
        writeHeader(typeSheet, List.of("题型", "题数"), headerStyle);
        int typeRow = 1;
        for (Map.Entry<String, long[]> entry : byType.entrySet()) {
            Row row = typeSheet.createRow(typeRow++);
            row.createCell(0).setCellValue(entry.getKey());
            row.createCell(1).setCellValue(entry.getValue()[0]);
        }

        Sheet masterySheet = workbook.createSheet("按掌握度统计");
        writeHeader(masterySheet, List.of("掌握度", "题数"), headerStyle);
        int masteryRow = 1;
        for (Map.Entry<String, long[]> entry : byMastery.entrySet()) {
            Row row = masterySheet.createRow(masteryRow++);
            row.createCell(0).setCellValue(entry.getKey());
            row.createCell(1).setCellValue(entry.getValue()[0]);
        }

        Sheet infoSheet = workbook.createSheet("导出信息");
        writeHeader(infoSheet, List.of("项", "值"), headerStyle);
        int infoRow = 1;
        infoRow = writeInfo(infoSheet, infoRow, "导出时间",
                LocalDateTime.now(ZONE).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        infoRow = writeInfo(infoSheet, infoRow, "题目总数", String.valueOf(rows.size()));
        infoRow = writeInfo(infoSheet, infoRow, "是否含答案", includeAnswer ? "是" : "否");
        writeInfo(infoSheet, infoRow, "是否含解析", includeExplanation ? "是" : "否");
        infoSheet.setColumnWidth(0, 20 * 256);
        infoSheet.setColumnWidth(1, 40 * 256);

        return workbook;
    }

    private int writeInfo(Sheet sheet, int rowIndex, String key, String value) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(key);
        row.createCell(1).setCellValue(value);
        return rowIndex + 1;
    }

    private void writeHeader(Sheet sheet, List<String> headers, CellStyle style) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(style);
        }
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private String label(String type) {
        return switch (type) {
            case "SA" -> TYPE_LABEL_SA;
            case "MC" -> TYPE_LABEL_MC;
            default -> TYPE_LABEL_TF;
        };
    }

    private String masteryLabel(String status) {
        return switch (status) {
            case "CORRECT" -> "答对";
            case "WRONG" -> "答错";
            case "VAGUE" -> "模糊";
            default -> "未做";
        };
    }

    private String oneLine(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }
}
