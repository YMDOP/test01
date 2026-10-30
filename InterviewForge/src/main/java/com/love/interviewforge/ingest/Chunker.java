package com.love.interviewforge.ingest;

import java.util.ArrayList;
import java.util.List;

/**
 * 切片器：标题优先、段落次之、句末兜底，目标 300–800 字。
 *
 * <p>产出保证：每个 draft 长度落在 [MIN, MAX]（除非整篇不足 MIN，由调用方丢弃文档）。</p>
 */
public class Chunker {

    public static final int MIN = 300;
    public static final int MAX = 800;
    private static final int OVERLAP = 50;
    /** 单段上限留出重叠空间，避免拼进缓冲后超长。 */
    private static final int SEGMENT_MAX = MAX - OVERLAP;

    public record ChunkDraft(String headingPath, String text) {
    }

    public List<ChunkDraft> chunk(List<ParsedBlock> blocks) {
        List<ParsedBlock> segments = new ArrayList<>();
        for (ParsedBlock block : blocks) {
            for (String segment : splitToSegments(block.text())) {
                segments.add(new ParsedBlock(block.headingPath(), segment));
            }
        }

        List<ChunkDraft> drafts = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        String heading = null;

        for (ParsedBlock segment : segments) {
            if (buffer.length() == 0) {
                heading = segment.headingPath();
            }
            int addition = segment.text().length() + (buffer.length() > 0 ? 1 : 0);
            if (buffer.length() > 0 && buffer.length() + addition > MAX) {
                drafts.add(new ChunkDraft(heading, buffer.toString().trim()));
                String tail = tail(buffer.toString(), OVERLAP);
                buffer.setLength(0);
                buffer.append(tail);
                heading = segment.headingPath();
            }
            if (buffer.length() > 0) {
                buffer.append('\n');
            }
            buffer.append(segment.text());
        }

        if (buffer.length() > 0) {
            String leftover = buffer.toString().trim();
            if (leftover.length() >= MIN) {
                drafts.add(new ChunkDraft(heading, leftover));
            } else if (!drafts.isEmpty()) {
                ChunkDraft previous = drafts.get(drafts.size() - 1);
                drafts.add(new ChunkDraft(heading, padToMin(leftover, previous.text())));
            } else {
                drafts.add(new ChunkDraft(heading, leftover));
            }
        }

        List<ChunkDraft> result = new ArrayList<>();
        for (ChunkDraft draft : drafts) {
            String text = draft.text();
            if (text.length() > MAX) {
                text = text.substring(0, MAX);
            }
            if (text.length() >= MIN) {
                result.add(new ChunkDraft(draft.headingPath(), text));
            }
        }
        return result;
    }

    private List<String> splitToSegments(String text) {
        List<String> segments = new ArrayList<>();
        if (text.length() <= SEGMENT_MAX) {
            segments.add(text);
            return segments;
        }
        StringBuilder current = new StringBuilder();
        for (String sentence : text.split("(?<=[。！？!?\\n])")) {
            if (sentence.isBlank()) {
                continue;
            }
            if (current.length() + sentence.length() > SEGMENT_MAX && current.length() > 0) {
                segments.add(current.toString().trim());
                current.setLength(0);
            }
            if (sentence.length() > SEGMENT_MAX) {
                // 单句超长：硬切
                segments.add(sentence.substring(0, SEGMENT_MAX));
                current.append(sentence.substring(SEGMENT_MAX));
            } else {
                current.append(sentence);
            }
        }
        if (current.length() > 0) {
            segments.add(current.toString().trim());
        }
        return segments;
    }

    private String padToMin(String text, String source) {
        if (text.length() >= MIN || source.isEmpty()) {
            return text;
        }
        int need = MIN - text.length();
        String prefix = source.length() <= need ? source : source.substring(source.length() - need);
        String merged = prefix + "\n" + text;
        // 补齐用的是重复内容，超出上限时从头部裁掉
        return merged.length() > MAX ? merged.substring(merged.length() - MAX) : merged;
    }

    private String tail(String text, int size) {
        return text.length() <= size ? text : text.substring(text.length() - size);
    }
}
