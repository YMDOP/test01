package com.love.interviewforge.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.love.interviewforge.common.BusinessException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * AI 阅卷。只对照题库里的标准答案，不允许引入标准答案以外的知识点。
 */
@Service
public class JudgeService {

    private static final Logger log = LoggerFactory.getLogger(JudgeService.class);
    private static final String SYSTEM = "你是阅卷老师。只输出 JSON，不要解释。";

    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    public JudgeService(AiClient aiClient, ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
    }

    public record Feedback(double score, List<String> hitPoints, List<String> missedPoints,
                           String suggestion, String hash) {
    }

    /** 简答判分。答案哈希用于重复提交时命中缓存。 */
    public Feedback grade(Long questionId, String stem, String standardAnswer, String explanation,
                          String userAnswer, double maxScore) {
        String hash = hash(questionId, maxScore, userAnswer);

        String prompt = """
                你是阅卷老师。请严格对照【标准答案】给【考生答案】打分，不得引入标准答案以外的知识点。

                【题干】%s
                【标准答案】%s
                【参考解析】%s
                【考生答案】%s
                【满分】%s

                评分规则：
                1. 按标准答案中的关键点逐点给分，漏点扣分，多答不额外加分
                2. 考生答案为空或明显跑题，得 0 分
                3. 不得因为「说法不同但意思相同」判错，也不得因为「看起来专业」给分
                4. hitPoints 与 missedPoints 都不能是空数组：没有命中就写明「未命中标准答案要点」，
                   没有遗漏就写明「无遗漏」

                只输出 JSON：
                {"score": 0, "hitPoints": ["命中的要点"], "missedPoints": ["遗漏的要点"],
                 "suggestion": "一句可执行的改进建议"}
                """.formatted(stem, standardAnswer, explanation,
                userAnswer == null || userAnswer.isBlank() ? "（未作答）" : userAnswer,
                trim(maxScore));

        AiClient.ChatResult result = aiClient.chat("GRADE", SYSTEM, prompt, 1200);
        JsonNode root;
        try {
            root = objectMapper.readTree(clean(result.content()));
        } catch (RuntimeException e) {
            throw new BusinessException(3001, "判分结果无法解析");
        }

        double score = Math.min(Math.max(root.path("score").asDouble(0), 0), maxScore);
        List<String> hits = readList(root.path("hitPoints"));
        List<String> missed = readList(root.path("missedPoints"));
        String suggestion = root.path("suggestion").asString("").trim();

        if (hits.isEmpty()) {
            hits = List.of("未命中标准答案要点");
        }
        if (missed.isEmpty()) {
            missed = List.of("无遗漏");
        }
        if (suggestion.isEmpty()) {
            suggestion = "对照标准答案补充遗漏要点";
        }

        log.debug("判分完成: question={} score={}/{}", questionId, score, maxScore);
        return new Feedback(score, hits, missed, suggestion, hash);
    }

    public static String hash(Long questionId, double maxScore, String userAnswer) {
        String raw = questionId + "|" + maxScore + "|" + normalize(userAnswer);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(raw.hashCode());
        }
    }

    private static String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private List<String> readList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(item -> {
                String text = item.asString("").trim();
                if (!text.isEmpty()) {
                    values.add(text);
                }
            });
        }
        return values;
    }

    private String clean(String content) {
        if (content == null) {
            return "{}";
        }
        String cleaned = content.trim();
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            int lastFence = cleaned.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                cleaned = cleaned.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return cleaned;
    }

    private String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
