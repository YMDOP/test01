package com.love.interviewforge.ingest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * pdf 解析：按页抽文本层，再按空行切段。扫描件抽不到文本时返回空列表，
 * 由上层记 parse_error，不做 OCR。
 */
public class PdfParser {

    private static final int MAX_PAGES = 200;

    public List<ParsedBlock> parse(Path file) throws IOException {
        List<ParsedBlock> blocks = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            int pages = Math.min(document.getNumberOfPages(), MAX_PAGES);
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            for (int page = 1; page <= pages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document);
                if (text == null || text.isBlank()) {
                    continue;
                }
                for (String paragraph : text.split("\\n\\s*\\n")) {
                    String trimmed = paragraph.trim();
                    if (trimmed.length() >= 40) {
                        blocks.add(new ParsedBlock("第 " + page + " 页", trimmed));
                    }
                }
            }
        }
        return blocks;
    }
}
