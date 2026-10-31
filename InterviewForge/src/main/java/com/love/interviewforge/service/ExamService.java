package com.love.interviewforge.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.love.interviewforge.common.BusinessException;
import com.love.interviewforge.domain.Exam;
import com.love.interviewforge.domain.ExamItem;
import com.love.interviewforge.domain.ExamItemRepository;
import com.love.interviewforge.domain.ExamRepository;
import com.love.interviewforge.domain.Mastery;
import com.love.interviewforge.domain.MasteryRepository;
import com.love.interviewforge.domain.WrongBook;
import com.love.interviewforge.domain.WrongBookRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 模拟面试：组卷 → 作答 → 交卷判分 → 报告。
 *
 * <p>客观题本地比对，简答调 AI 对照标准答案阅卷；同一答案重复提交命中缓存。</p>
 */
@Service
public class ExamService {

    private static final int SA_COUNT = 10;
    private static final int MC_COUNT = 3;
    private static final int TF_COUNT = 2;
    private static final double SA_SCORE = 8;
    private static final double MC_SCORE = 4;
    private static final double TF_SCORE = 4;
    private static final int DURATION_MINUTES = 45;

    private final ExamRepository examRepository;
    private final ExamItemRepository itemRepository;
    private final MasteryRepository masteryRepository;
    private final WrongBookRepository wrongBookRepository;
    private final PickService pickService;
    private final JudgeService judgeService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public ExamService(ExamRepository examRepository,
                       ExamItemRepository itemRepository,
                       MasteryRepository masteryRepository,
                       WrongBookRepository wrongBookRepository,
                       PickService pickService,
                       JudgeService judgeService,
                       JdbcTemplate jdbcTemplate,
                       ObjectMapper objectMapper,
                       PlatformTransactionManager transactionManager) {
        this.examRepository = examRepository;
        this.itemRepository = itemRepository;
        this.masteryRepository = masteryRepository;
        this.wrongBookRepository = wrongBookRepository;
        this.pickService = pickService;
        this.judgeService = judgeService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // ------------------------------------------------------------
    // 对外模型
    // ------------------------------------------------------------

    /** 题面：绝不包含 standardAnswer / explanation。 */
    public record ExamQuestion(Long questionId, int orderNo, String type, String stem,
                               String optionsJson, double maxScore) {
    }

    public record ExamView(Long examId, String status, String startedAt, int durationMinutes,
                           double maxScore, List<ExamQuestion> items) {
    }

    public record ReportItem(Long questionId, int orderNo, String type, String stem, String optionsJson,
                             String userAnswer, String standardAnswer, String explanation,
                             double score, double maxScore, List<String> hitPoints,
                             List<String> missedPoints, String suggestion) {
    }

    public record Report(Long examId, String status, double totalScore, double maxScore,
                         long durationSec, String startedAt, String submittedAt,
                         List<ReportItem> items) {
    }

    public record AnswerRequest(Long questionId, String answer) {
    }

    // ------------------------------------------------------------
    // 组卷
    // ------------------------------------------------------------

    @Transactional
    public ExamView create() {
        List<Long> ids = pickBalanced();
        if (ids.isEmpty()) {
            throw new BusinessException(2001, "题库为空，无法组卷");
        }

        Exam exam = new Exam();
        exam.status = "RUNNING";
        exam.startedAt = Instant.now().toString();
        exam.blueprintJson = "{\"sa\":10,\"mc\":3,\"tf\":2,\"minutes\":" + DURATION_MINUTES + "}";
        exam = examRepository.saveAndFlush(exam);

        Map<Long, QuestionService.QuestionView> views = loadViews(ids);
        List<ExamItem> items = new ArrayList<>();
        List<ExamQuestion> questions = new ArrayList<>();
        int order = 1;
        double maxScore = 0;

        for (Long id : ids) {
            QuestionService.QuestionView view = views.get(id);
            if (view == null) {
                continue;
            }
            double itemMax = scoreOf(view.type());

            ExamItem item = new ExamItem();
            item.examId = exam.id;
            item.questionId = id;
            item.orderNo = order;
            item.maxScore = itemMax;
            item.judged = "PENDING";
            itemRepository.save(item);

            items.add(item);
            questions.add(new ExamQuestion(id, order, view.type(), view.stem(), view.optionsJson(), itemMax));
            maxScore += itemMax;
            order++;
        }

        exam.maxScore = maxScore;
        examRepository.save(exam);
        return new ExamView(exam.id, exam.status, exam.startedAt, DURATION_MINUTES, maxScore, questions);
    }

    private List<Long> pickBalanced() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, category_id, type FROM question");
        Map<String, Map<Long, List<Long>>> buckets = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String type = String.valueOf(row.get("type"));
            long categoryId = ((Number) row.get("category_id")).longValue();
            long id = ((Number) row.get("id")).longValue();
            buckets.computeIfAbsent(type, key -> new LinkedHashMap<>())
                    .computeIfAbsent(categoryId, key -> new ArrayList<>())
                    .add(id);
        }

        List<Long> result = new ArrayList<>();
        result.addAll(takeRoundRobin(buckets.get("SA"), SA_COUNT));
        result.addAll(takeRoundRobin(buckets.get("MC"), MC_COUNT));
        result.addAll(takeRoundRobin(buckets.get("TF"), TF_COUNT));
        return result;
    }

    /** 分类轮转取题，避免一张卷全来自同一个主题。 */
    private List<Long> takeRoundRobin(Map<Long, List<Long>> byCategory, int count) {
        if (byCategory == null || byCategory.isEmpty()) {
            return List.of();
        }
        List<List<Long>> lists = new ArrayList<>(byCategory.values());
        lists.forEach(Collections::shuffle);

        List<Long> picked = new ArrayList<>();
        int round = 0;
        while (picked.size() < count) {
            boolean added = false;
            for (List<Long> list : lists) {
                if (round < list.size() && picked.size() < count) {
                    picked.add(list.get(round));
                    added = true;
                }
            }
            if (!added) {
                break;
            }
            round++;
        }
        return picked;
    }

    private double scoreOf(String type) {
        return switch (type) {
            case "SA" -> SA_SCORE;
            case "MC" -> MC_SCORE;
            default -> TF_SCORE;
        };
    }

    // ------------------------------------------------------------
    // 作答
    // ------------------------------------------------------------

    @Transactional
    public void answer(Long examId, Long questionId, String answer) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new BusinessException(2001, "试卷不存在: " + examId));
        if (!"RUNNING".equals(exam.status)) {
            throw new BusinessException(2001, "试卷已交卷，不能再作答");
        }
        ExamItem item = itemRepository.findByExamIdOrderByOrderNoAsc(examId).stream()
                .filter(candidate -> candidate.questionId.equals(questionId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(2001, "该题不在本卷中"));
        item.userAnswer = answer;
        itemRepository.saveAndFlush(item);
    }

    @Transactional(readOnly = true)
    public ExamView get(Long examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new BusinessException(2001, "试卷不存在: " + examId));
        List<ExamItem> items = itemRepository.findByExamIdOrderByOrderNoAsc(examId);
        Map<Long, QuestionService.QuestionView> views = loadViews(
                items.stream().map(item -> item.questionId).toList());

        List<ExamQuestion> questions = items.stream()
                .map(item -> {
                    QuestionService.QuestionView view = views.get(item.questionId);
                    return new ExamQuestion(item.questionId, item.orderNo, view.type(), view.stem(),
                            view.optionsJson(), item.maxScore == null ? 0 : item.maxScore);
                })
                .toList();
        return new ExamView(exam.id, exam.status, exam.startedAt, DURATION_MINUTES,
                exam.maxScore == null ? 0 : exam.maxScore, questions);
    }

    // ------------------------------------------------------------
    // 交卷判分
    // ------------------------------------------------------------

    public Report submit(Long examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new BusinessException(2001, "试卷不存在: " + examId));
        List<ExamItem> items = itemRepository.findByExamIdOrderByOrderNoAsc(examId);
        if (items.isEmpty()) {
            throw new BusinessException(2001, "试卷没有题目");
        }
        Map<Long, QuestionService.QuestionView> views = loadViews(
                items.stream().map(item -> item.questionId).toList());

        // 判分阶段不占用数据库事务：AI 调用可能持续几十秒，
        // 期间 ai_usage 走独立事务写入，长事务会触发 SQLITE_BUSY_SNAPSHOT。
        List<Judged> judgedList = new ArrayList<>();
        for (ExamItem item : items) {
            QuestionService.QuestionView view = views.get(item.questionId);
            if (view == null) {
                judgedList.add(null);
                continue;
            }
            double itemMax = item.maxScore == null ? scoreOf(view.type()) : item.maxScore;
            judgedList.add(judgeItem(item, view, itemMax));
        }

        return transactionTemplate.execute(status -> persistResult(exam, items, views, judgedList));
    }

    private Report persistResult(Exam exam, List<ExamItem> items,
                                 Map<Long, QuestionService.QuestionView> views,
                                 List<Judged> judgedList) {
        double total = 0;
        double maxTotal = 0;
        List<ReportItem> reportItems = new ArrayList<>();
        String judgedAt = Instant.now().toString();

        for (int i = 0; i < items.size(); i++) {
            ExamItem item = items.get(i);
            QuestionService.QuestionView view = views.get(item.questionId);
            Judged judged = judgedList.get(i);
            if (view == null || judged == null) {
                continue;
            }
            double itemMax = item.maxScore == null ? scoreOf(view.type()) : item.maxScore;
            item.score = judged.score();
            item.aiFeedbackJson = judged.feedbackJson();
            item.judged = "OK";
            item.judgedAt = judgedAt;
            itemRepository.save(item);

            total += judged.score();
            maxTotal += itemMax;
            reportItems.add(new ReportItem(item.questionId, item.orderNo, view.type(), view.stem(),
                    view.optionsJson(), item.userAnswer, view.standardAnswer(), view.explanation(),
                    judged.score(), itemMax, judged.hitPoints(), judged.missedPoints(),
                    judged.suggestion()));

            updateMastery(item.questionId, judged.score(), itemMax);
        }

        exam.status = "SUBMITTED";
        exam.submittedAt = Instant.now().toString();
        exam.score = total;
        exam.maxScore = maxTotal;
        try {
            exam.durationSec = Duration.between(
                    Instant.parse(exam.startedAt), Instant.parse(exam.submittedAt)).getSeconds();
        } catch (RuntimeException ignored) {
            exam.durationSec = 0L;
        }
        examRepository.save(exam);

        return new Report(exam.id, exam.status, total, maxTotal,
                exam.durationSec == null ? 0 : exam.durationSec, exam.startedAt, exam.submittedAt,
                reportItems);
    }
    private record Judged(double score, List<String> hitPoints, List<String> missedPoints,
                          String suggestion, String feedbackJson) {
    }

    private Judged judgeItem(ExamItem item, QuestionService.QuestionView view, double maxScore) {
        String userAnswer = item.userAnswer == null ? "" : item.userAnswer.trim();

        if (!"SA".equals(view.type())) {
            boolean correct = objectiveCorrect(view.type(), userAnswer, view.standardAnswer());
            double score = correct ? maxScore : 0;
            String hit = correct ? "客观题作答正确" : "客观题作答错误";
            String missed = correct ? "无" : "正确答案：" + view.standardAnswer();
            String json = toJson(Map.of(
                    "hash", "objective:" + JudgeService.hash(view.id(), maxScore, userAnswer),
                    "score", score,
                    "hitPoints", List.of(hit),
                    "missedPoints", List.of(missed),
                    "suggestion", correct ? "保持" : "回看解析，注意易混概念"));
            return new Judged(score, List.of(hit), List.of(missed),
                    correct ? "保持" : "回看解析，注意易混概念", json);
        }

        // 简答：先看缓存
        String expectedHash = JudgeService.hash(view.id(), maxScore, userAnswer);
        Cached cached = readCache(item.aiFeedbackJson, expectedHash);
        if (cached != null) {
            return new Judged(cached.score(), cached.hitPoints(), cached.missedPoints(),
                    cached.suggestion(), item.aiFeedbackJson);
        }

        JudgeService.Feedback feedback = judgeService.grade(view.id(), view.stem(),
                view.standardAnswer(), view.explanation(), userAnswer, maxScore);
        String json = toJson(Map.of(
                "hash", feedback.hash(),
                "score", feedback.score(),
                "hitPoints", feedback.hitPoints(),
                "missedPoints", feedback.missedPoints(),
                "suggestion", feedback.suggestion()));
        return new Judged(feedback.score(), feedback.hitPoints(), feedback.missedPoints(),
                feedback.suggestion(), json);
    }

    private record Cached(double score, List<String> hitPoints, List<String> missedPoints,
                          String suggestion) {
    }

    private Cached readCache(String feedbackJson, String expectedHash) {
        if (feedbackJson == null || feedbackJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(feedbackJson);
            if (!expectedHash.equals(root.path("hash").asString(""))) {
                return null;
            }
            List<String> hits = new ArrayList<>();
            root.path("hitPoints").forEach(node -> hits.add(node.asString("")));
            List<String> missed = new ArrayList<>();
            root.path("missedPoints").forEach(node -> missed.add(node.asString("")));
            return new Cached(root.path("score").asDouble(0), hits, missed,
                    root.path("suggestion").asString(""));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean objectiveCorrect(String type, String userAnswer, String standardAnswer) {
        if (userAnswer.isBlank()) {
            return false;
        }
        if ("MC".equals(type)) {
            return letter(userAnswer).equals(letter(standardAnswer));
        }
        return normalizeTf(userAnswer).equals(normalizeTf(standardAnswer));
    }

    private String letter(String value) {
        if (value == null) {
            return "";
        }
        for (char c : value.trim().toUpperCase().toCharArray()) {
            if (c >= 'A' && c <= 'F') {
                return String.valueOf(c);
            }
        }
        return "";
    }

    private String normalizeTf(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim().toUpperCase();
        if (text.startsWith("T") || "对".equals(text) || "正确".equals(text) || "是".equals(text)
                || "Y".equals(text) || "YES".equals(text) || "1".equals(text)) {
            return "TRUE";
        }
        if (text.startsWith("F") || "错".equals(text) || "错误".equals(text) || "否".equals(text)
                || "N".equals(text) || "NO".equals(text) || "0".equals(text)) {
            return "FALSE";
        }
        return text;
    }

    private void updateMastery(Long questionId, double score, double maxScore) {
        double ratio = maxScore <= 0 ? 0 : score / maxScore;
        boolean correct = ratio >= 0.6;

        Mastery mastery = masteryRepository.findById(questionId).orElseGet(() -> {
            Mastery created = new Mastery();
            created.questionId = questionId;
            created.status = "UNTRIED";
            return created;
        });
        mastery.status = correct ? "CORRECT" : "WRONG";
        if (correct) {
            mastery.correctCount++;
        } else {
            mastery.wrongCount++;
        }
        mastery.lastAt = Instant.now().toString();
        masteryRepository.save(mastery);

        WrongBook existing = wrongBookRepository.findByQuestionId(questionId).orElse(null);
        if (correct) {
            if (existing != null && existing.resolvedAt == null) {
                existing.resolvedAt = Instant.now().toString();
                wrongBookRepository.save(existing);
            }
        } else if (existing == null) {
            WrongBook entry = new WrongBook();
            entry.questionId = questionId;
            entry.sourceType = "EXAM";
            entry.createdAt = Instant.now().toString();
            wrongBookRepository.save(entry);
        } else if (existing.resolvedAt != null) {
            existing.resolvedAt = null;
            wrongBookRepository.save(existing);
        }
    }

    private Map<Long, QuestionService.QuestionView> loadViews(List<Long> ids) {
        Map<Long, QuestionService.QuestionView> map = new HashMap<>();
        for (QuestionService.QuestionView view : pickService.pickByIds(ids)) {
            map.put(view.id(), view);
        }
        return map;
    }

    @Transactional(readOnly = true)
    public Report result(Long examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new BusinessException(2001, "试卷不存在: " + examId));
        if (!"SUBMITTED".equals(exam.status)) {
            throw new BusinessException(2001, "试卷尚未交卷");
        }
        List<ExamItem> items = itemRepository.findByExamIdOrderByOrderNoAsc(examId);
        Map<Long, QuestionService.QuestionView> views = loadViews(
                items.stream().map(item -> item.questionId).toList());

        List<ReportItem> reportItems = items.stream()
                .map(item -> {
                    QuestionService.QuestionView view = views.get(item.questionId);
                    Cached cached = readCache(item.aiFeedbackJson,
                            JudgeService.hash(item.questionId, item.maxScore == null ? 0 : item.maxScore,
                                    item.userAnswer == null ? "" : item.userAnswer));
                    double score = item.score == null ? 0 : item.score;
                    double itemMax = item.maxScore == null ? 0 : item.maxScore;
                    List<String> hits = cached == null ? List.of() : cached.hitPoints();
                    List<String> missed = cached == null ? List.of() : cached.missedPoints();
                    String suggestion = cached == null ? "" : cached.suggestion();
                    return new ReportItem(item.questionId, item.orderNo, view.type(), view.stem(),
                            view.optionsJson(), item.userAnswer, view.standardAnswer(),
                            view.explanation(), score, itemMax, hits, missed, suggestion);
                })
                .sorted(Comparator.comparingInt(ReportItem::orderNo))
                .toList();

        return new Report(exam.id, exam.status, exam.score == null ? 0 : exam.score,
                exam.maxScore == null ? 0 : exam.maxScore, exam.durationSec == null ? 0 : exam.durationSec,
                exam.startedAt, exam.submittedAt, reportItems);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> history() {
        return jdbcTemplate.queryForList("""
                SELECT id, status, started_at, submitted_at, score, max_score
                  FROM exam
                 ORDER BY id DESC
                 LIMIT 20
                """);
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException e) {
            throw new BusinessException(3001, "判分结果序列化失败");
        }
    }
}
