package com.love.interviewforge.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.love.interviewforge.common.BusinessException;
import com.love.interviewforge.domain.Chunk;
import com.love.interviewforge.domain.ChunkRepository;
import com.love.interviewforge.domain.Document;
import com.love.interviewforge.domain.DocumentRepository;
import com.love.interviewforge.ingest.Chunker;
import com.love.interviewforge.ingest.DirectoryScanner;
import com.love.interviewforge.ingest.DocxParser;
import com.love.interviewforge.ingest.MarkdownParser;
import com.love.interviewforge.ingest.ParsedBlock;
import com.love.interviewforge.ingest.PdfParser;
import com.love.interviewforge.ingest.TextUtils;

/**
 * 文档扫描与导入。扫描只读，导入才落库。
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final int MAX_SCAN_ITEMS = 300;

    private final DirectoryScanner scanner = new DirectoryScanner();
    private final MarkdownParser markdownParser = new MarkdownParser();
    private final DocxParser docxParser = new DocxParser();
    private final PdfParser pdfParser = new PdfParser();
    private final Chunker chunker = new Chunker();

    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;

    public DocumentService(DocumentRepository documentRepository, ChunkRepository chunkRepository) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
    }

    public record ScanItem(String path, String filename, String ext, long size) {
    }

    public record ScanSummary(String root, int total, List<ScanItem> items, boolean truncated) {
    }

    public record ImportSummary(int scanned, int imported, int skipped, int failed,
                                int chunks, long elapsedMs, List<String> messages) {
    }

    /** 只读扫描，不写库。 */
    public ScanSummary scan(String dir) {
        Path root = Paths.get(dir);
        try {
            DirectoryScanner.ScanResult result = scanner.scan(root);
            List<ScanItem> items = new ArrayList<>();
            int count = 0;
            for (Path file : result.files()) {
                if (count++ >= MAX_SCAN_ITEMS) {
                    break;
                }
                long size;
                try {
                    size = Files.size(file);
                } catch (IOException e) {
                    size = 0;
                }
                items.add(new ScanItem(
                        file.toString(),
                        file.getFileName().toString(),
                        extension(file),
                        size));
            }
            return new ScanSummary(root.toString(), result.files().size(), items,
                    result.files().size() > items.size());
        } catch (IOException e) {
            throw new BusinessException(1002, "目录扫描失败：" + e.getMessage());
        }
    }

    /** 导入并切片。已导入过的文件按 sha256 跳过。 */
    public ImportSummary importDocuments(String dir) {
        long start = System.currentTimeMillis();
        Path root = Paths.get(dir);
        DirectoryScanner.ScanResult scanResult;
        try {
            scanResult = scanner.scan(root);
        } catch (IOException e) {
            throw new BusinessException(1002, "目录扫描失败：" + e.getMessage());
        }

        Set<String> existingFingerprints = new HashSet<>();
        chunkRepository.findAll().forEach(chunk -> {
            if (chunk.fingerprint != null) {
                existingFingerprints.add(chunk.fingerprint);
            }
        });

        int imported = 0;
        int skipped = 0;
        int failed = 0;
        int chunkCount = 0;
        List<String> messages = new ArrayList<>();

        for (Path file : scanResult.files()) {
            try {
                String sha256 = TextUtils.sha256(Files.readAllBytes(file));
                if (documentRepository.findBySha256(sha256).isPresent()) {
                    skipped++;
                    continue;
                }

                List<ParsedBlock> blocks = parse(file);
                Document document = new Document();
                document.path = file.toString();
                document.filename = file.getFileName().toString();
                document.ext = extension(file);
                document.size = Files.size(file);
                document.sha256 = sha256;
                document.importedAt = Instant.now().toString();

                if (blocks.isEmpty()) {
                    document.parseStatus = "FAILED";
                    document.parseError = "未提取到文本（可能是扫描件或空文档）";
                    documentRepository.save(document);
                    failed++;
                    messages.add(document.filename + ": 未提取到文本");
                    continue;
                }

                List<Chunker.ChunkDraft> drafts = chunker.chunk(blocks);
                if (drafts.isEmpty()) {
                    document.parseStatus = "SKIPPED";
                    document.parseError = "正文不足 " + Chunker.MIN + " 字";
                    documentRepository.save(document);
                    skipped++;
                    messages.add(document.filename + ": 正文过短，已跳过");
                    continue;
                }

                document.parseStatus = "OK";
                document.chunkCount = drafts.size();
                document = documentRepository.save(document);

                int ordinal = 0;
                for (Chunker.ChunkDraft draft : drafts) {
                    String fingerprint = TextUtils.fingerprint(draft.text());
                    if (!existingFingerprints.add(fingerprint)) {
                        continue;
                    }
                    Chunk chunk = new Chunk();
                    chunk.documentId = document.id;
                    chunk.ordinal = ordinal++;
                    chunk.headingPath = draft.headingPath();
                    chunk.text = draft.text();
                    chunk.charCount = draft.text().length();
                    chunk.fingerprint = fingerprint;
                    chunkRepository.save(chunk);
                    chunkCount++;
                }
                imported++;
            } catch (IOException e) {
                failed++;
                messages.add(file.getFileName() + ": " + e.getMessage());
            } catch (RuntimeException e) {
                failed++;
                messages.add(file.getFileName() + ": 解析异常 " + e.getClass().getSimpleName());
                log.warn("导入失败: {}", file, e);
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("导入完成: 扫描={} 导入={} 跳过={} 失败={} 切片={} 用时={}ms",
                scanResult.files().size(), imported, skipped, failed, chunkCount, elapsed);
        return new ImportSummary(scanResult.files().size(), imported, skipped, failed,
                chunkCount, elapsed, messages);
    }

    private List<ParsedBlock> parse(Path file) throws IOException {
        return switch (extension(file)) {
            case "md" -> markdownParser.parse(file);
            case "docx" -> docxParser.parse(file);
            case "pdf" -> pdfParser.parse(file);
            default -> List.of();
        };
    }

    private String extension(Path file) {
        String name = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1) : "";
    }

    @Transactional(readOnly = true)
    public List<Document> list() {
        return documentRepository.findAll(org.springframework.data.domain.Sort.by("id"));
    }
}
