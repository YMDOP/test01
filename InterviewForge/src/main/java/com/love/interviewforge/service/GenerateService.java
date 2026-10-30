package com.love.interviewforge.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.love.interviewforge.common.BusinessException;
import com.love.interviewforge.domain.Category;
import com.love.interviewforge.domain.CategoryRepository;
import com.love.interviewforge.domain.Chunk;
import com.love.interviewforge.domain.ChunkRepository;
import com.love.interviewforge.domain.GenerateJob;
import com.love.interviewforge.domain.GenerateJobRepository;
import com.love.interviewforge.domain.GenerateJobStep;
import com.love.interviewforge.domain.GenerateJobStepRepository;
import com.love.interviewforge.domain.Question;
import com.love.interviewforge.domain.QuestionRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 分类 + 出题流水线。
 *
 * <p>两阶段：CLASSIFY（切片归入知识分类）→ GENERATE（按分类出题）。
 * 任务状态持久化到 generate_job / generate_job_step，进程重启后可续跑。</p>
 */
@Service
public class GenerateService {

    private static final Logger log = LoggerFactory.getLogger(GenerateService.class);

    private static final int CLASSIFY_BATCH = 20;
    private static final int MAX_CHUNKS_PER_CATEGORY = 30;
    /** 出题阶段最多处理的分类数，防止 AI 把分类拆得过细导致调用爆炸。 */
    private static final int MAX_GENERATE_CATEGORIES = 15;
    private static final double CONFIRM_THRESHOLD = 5.0d;
    private static final int DEFAULT_MAX_CHUNKS = 300;

    private static final String SYSTEM_CLASSIFY = "你是 Java 后端面试资料整理助手。只输出 JSON，不要解释。";
    private static final String SYSTEM_GENERATE = "你是拥有 5 年经验的 Java 后端面试官，面向校招候选人出题。只输出 JSON，不要解释。";

    private final ChunkRepository chunkRepository;
    private final CategoryRepository categoryRepository;
    private final QuestionRepository questionRepository;
    private final GenerateJobRepository jobRepository;
    private final GenerateJobStepRepository stepRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AiClient aiClient;
    private final PricingService pricingService;
    private final ObjectMapper objectMapper;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "generate-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, List<SseEmitter>> subscribers = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> running = new ConcurrentHashMap<>();

    public GenerateService(ChunkRepository chunkRepository,
                           CategoryRepository categoryRepository,
                           QuestionRepository questionRepository,
                           GenerateJobRepository jobRepository,
                           GenerateJobStepRepository stepRepository,
                           JdbcTemplate jdbcTemplate,
                           AiClient aiClient,
                           PricingService pricingService,
                           ObjectMapper objectMapper) {
        this.chunkRepository = chunkRepository;
        this.categoryRepository = categoryRepository;
        this.questionRepository = questionRepository;
        this.jobRepository = jobRepository;
        this.stepRepository = stepRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.aiClient = aiClient;
        this.pricingService = pricingService;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------
    // 对外模型
    // ------------------------------------------------------------

    public record Estimate(int chunkCount, int estimatedCalls, double estimatedCost, double threshold,
                           boolean needConfirm) {
    }

    public record JobView(String id, String status, String stage, int progress, int totalChunks,
                          int totalCategories, int doneCategories, int questionCount,
                          double estimatedCost, String message) {
    }

    public record CreateResult(String jobId, boolean needConfirm, Estimate estimate) {
    }

    public record ResetResult(long questionsDeleted, long aiUsageKept) {
    }

    // ------------------------------------------------------------
    // 预估与创建
    // ------------------------------------------------------------

    public Estimate estimate(int maxChunks) {
        int budget = maxChunks > 0 ? maxChunks : DEFAULT_MAX_CHUNKS;
        long unclassified = chunkRepository.countByCategoryIdIsNull();
        int chunkCount = (int) Math.min(budget, unclassified > 0 ? unclassified : 0);
        if (chunkCount == 0) {
            chunkCount = (int) Math.min(budget, chunkRepository.count());
        }
        int classifyCalls = (int) Math.ceil(chunkCount / (double) CLASSIFY_BATCH);
        int generateCalls = Math.min(MAX_GENERATE_CATEGORIES, Math.max(3, (int) Math.ceil(chunkCount / 60.0)));
        int calls = classifyCalls + generateCalls;

        PricingService.Price price = pricingService.priceFor("deepseek-chat");
        // 单次调用粗估：输入 5000 token（未命中）+ 输出 2500 token
        double perCall = (5000 * price.inputMiss() + 2500 * price.output()) / 1_000_000d;
        double cost = calls * perCall;
        return new Estimate(chunkCount, calls, round6(cost), CONFIRM_THRESHOLD, cost > CONFIRM_THRESHOLD);
    }

    public CreateResult create(int maxChunks, boolean confirm) {
        Estimate estimate = estimate(maxChunks);
        if (estimate.chunkCount() == 0) {
            throw new BusinessException(2001, "没有可用的切片，请先导入文档");
        }
        if (estimate.needConfirm() && !confirm) {
            return new CreateResult(null, true, estimate);
        }

        GenerateJob job = new GenerateJob();
        job.id = UUID.randomUUID().toString();
        job.status = "PENDING";
        job.stage = "CLASSIFY";
        job.paramsJson = "{\"maxChunks\":" + estimate.chunkCount() + "}";
        job.progress = 0;
        job.estimatedCost = estimate.estimatedCost();
        job.message = "排队中";
        job.createdAt = Instant.now().toString();
        job.updatedAt = job.createdAt;
        jobRepository.save(job);

        start(job.id);
        return new CreateResult(job.id, false, estimate);
    }

    // ------------------------------------------------------------
    // 执行
    // ------------------------------------------------------------

    public void start(String jobId) {
        AtomicBoolean flag = running.computeIfAbsent(jobId, key -> new AtomicBoolean(false));
        if (!flag.compareAndSet(false, true)) {
            return;
        }
        executor.submit(() -> {
            try {
                run(jobId);
            } finally {
                flag.set(false);
            }
        });
    }

    /** 重启后自动续跑未完成的任务。 */
    @EventListener(ApplicationReadyEvent.class)
    public void resumePendingJobs() {
        List<GenerateJob> jobs = jobRepository.findByStatusIn(List.of("RUNNING"));
        for (GenerateJob job : jobs) {
            log.info("发现未完成任务，继续执行: {}", job.id);
            start(job.id);
        }
    }

    private void run(String jobId) {
        GenerateJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return;
        }
        try {
            job.status = "RUNNING";
            job.message = "开始处理";
            persist(job);
            emit(job, "progress");

            if (job.stage == null || "CLASSIFY".equals(job.stage)) {
                classify(job);
                job.stage = "GENERATE";
                persist(job);
            }
            generate(job);

            job.status = "DONE";
            job.stage = "FINISHED";
            job.progress = 100;
            job.message = "完成：共 " + job.questionCount + " 题";
            persist(job);
            emit(job, "done");
            completeEmitters(jobId);
            log.info("生成任务完成: id={} questions={}", jobId, job.questionCount);
        } catch (Exception e) {
            log.error("生成任务失败: id={}", jobId, e);
            job.status = "FAILED";
            job.message = "失败：" + e.getMessage();
            persist(job);
            emit(job, "error");
            completeEmitters(jobId);
        }
    }

    private void classify(GenerateJob job) {
        int budget = readMaxChunks(job);
        List<Chunk> chunks = chunkRepository.findByCategoryIdIsNullOrderByIdAsc();
        if (chunks.size() > budget) {
            chunks = chunks.subList(0, budget);
        }
        job.totalChunks = chunks.size();
        persist(job);

        int processed = 0;
        int failures = 0;
        for (int start = 0; start < chunks.size(); start += CLASSIFY_BATCH) {
            List<Chunk> batch = chunks.subList(start, Math.min(start + CLASSIFY_BATCH, chunks.size()));
            try {
                classifyBatch(batch);
            } catch (RuntimeException e) {
                failures++;
                log.warn("分类批次失败，跳过: start={} reason={}", start, e.getMessage());
            }
            processed += batch.size();
            job.progress = (int) (45.0 * processed / Math.max(1, chunks.size()));
            job.message = "分类中 " + Math.min(processed, chunks.size()) + "/" + chunks.size()
                    + (failures > 0 ? "（失败批次 " + failures + "）" : "");
            persist(job);
            emit(job, "progress");
        }
    }

    private void classifyBatch(List<Chunk> batch) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("下面是候选人笔记的 ").append(batch.size())
                .append(" 个片段，编号从 0 开始。请按技术知识点分组命名。\n\n");

        String existing = categoryRepository.findAll().stream()
                .map(category -> category.name)
                .limit(40)
                .collect(java.util.stream.Collectors.joining("、"));
        if (!existing.isEmpty()) {
            prompt.append("已有分类（优先复用，禁止为同一主题新建同义分类）：").append(existing).append("\n\n");
        }

        for (int i = 0; i < batch.size(); i++) {
            prompt.append("### 片段 ").append(i).append('\n')
                    .append(batch.get(i).text).append("\n\n");
        }
        prompt.append("""
                要求：
                1. 分类名不超过 10 个字，使用业界通用叫法（如「MySQL 优化」「消息队列」「Spring Security」）
                2. 每个片段必须且只能归入一个分类
                3. 全库分类总数控制在 10 到 15 个之间；不要为单个知识点单独建类，相近主题必须合并
                4. 输出 JSON：{"categories":[{"name":"分类名","description":"一句话说明"}],
                   "assignments":[{"index":0,"category":"分类名"}]}
                """);

        AiClient.ChatResult result = aiClient.chat("CLASSIFY", SYSTEM_CLASSIFY, prompt.toString(), 3000);
        JsonNode root = parseJson(result.content());

        Map<String, Category> byName = new HashMap<>();
        categoryRepository.findAll().forEach(category -> byName.put(category.name, category));

        JsonNode categories = root.path("categories");
        if (categories.isArray()) {
            for (JsonNode node : categories) {
                String name = node.path("name").asString("").trim();
                if (name.isEmpty()) {
                    continue;
                }
                Category category = byName.computeIfAbsent(name, key -> {
                    Category created = new Category();
                    created.name = key;
                    created.description = node.path("description").asString("");
                    created.origin = "AI";
                    created.createdAt = Instant.now().toString();
                    return categoryRepository.save(created);
                });
                byName.put(name, category);
            }
        }

        JsonNode assignments = root.path("assignments");
        if (assignments.isArray()) {
            for (JsonNode node : assignments) {
                int index = node.path("index").asInt(-1);
                String name = node.path("category").asString("").trim();
                if (index < 0 || index >= batch.size() || name.isEmpty()) {
                    continue;
                }
                Category category = byName.get(name);
                if (category == null) {
                    continue;
                }
                Chunk chunk = batch.get(index);
                chunk.categoryId = category.id;
                chunkRepository.save(chunk);
            }
        }
    }

    private void generate(GenerateJob job) {
        Map<Long, Long> chunkCounts = new HashMap<>();
        jdbcTemplate.query(
                "SELECT category_id, COUNT(*) AS c FROM chunk WHERE category_id IS NOT NULL GROUP BY category_id",
                (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                        chunkCounts.put(rs.getLong("category_id"), rs.getLong("c")));

        List<Category> ranked = categoryRepository.findAll().stream()
                .filter(category -> chunkCounts.getOrDefault(category.id, 0L) > 0)
                .sorted(Comparator.comparingLong((Category category) -> chunkCounts.getOrDefault(category.id, 0L))
                        .reversed())
                .toList();

        List<Category> categories = ranked.stream().limit(MAX_GENERATE_CATEGORIES).toList();
        if (ranked.size() > categories.size()) {
            log.info("分类过多（{} 个），本轮只处理切片最多的 {} 个", ranked.size(), MAX_GENERATE_CATEGORIES);
        }

        job.totalCategories = categories.size();
        persist(job);

        int done = 0;
        int totalQuestions = 0;
        for (Category category : categories) {
            GenerateJobStep step = stepRepository.findByJobIdAndCategoryId(job.id, category.id)
                    .orElseGet(() -> {
                        GenerateJobStep created = new GenerateJobStep();
                        created.jobId = job.id;
                        created.categoryId = category.id;
                        created.categoryName = category.name;
                        created.status = "PENDING";
                        created.updatedAt = Instant.now().toString();
                        return stepRepository.save(created);
                    });

            if ("DONE".equals(step.status)) {
                done++;
                totalQuestions += step.questionCount;
                progress(job, done, categories.size(), totalQuestions, category.name + " 已完成，跳过");
                continue;
            }

            try {
                int generated = generateCategory(job, category);
                step.status = "DONE";
                step.questionCount = generated;
                step.error = null;
                step.updatedAt = Instant.now().toString();
                stepRepository.save(step);
                totalQuestions += generated;
            } catch (RuntimeException e) {
                step.status = "FAILED";
                step.error = e.getMessage();
                step.updatedAt = Instant.now().toString();
                stepRepository.save(step);
                log.warn("分类出题失败: {} - {}", category.name, e.getMessage());
            }
            done++;
            progress(job, done, categories.size(), totalQuestions, category.name);
        }

        job.questionCount = totalQuestions;
        persist(job);
    }

    private int generateCategory(GenerateJob job, Category category) {
        List<Chunk> chunks = chunkRepository.findByCategoryIdOrderByIdAsc(category.id).stream()
                .limit(MAX_CHUNKS_PER_CATEGORY)
                .toList();

        StringBuilder prompt = new StringBuilder();
        prompt.append("【知识分类】").append(category.name).append("\n\n【参考资料】\n");
        for (int i = 0; i < chunks.size(); i++) {
            prompt.append("片段 ").append(i).append("：")
                    .append(chunks.get(i).text).append("\n\n");
        }
        prompt.append("""
                请基于上述资料出题，要求：
                1. 只依据参考资料，不得编造资料中没有的内容
                2. 优先出带项目场景的追问，不考纯背诵
                3. 题型比例：简答 SA 约 50%，选择 MC 约 30%，判断 TF 约 20%
                4. 选择题给 4 个选项，干扰项取自易混概念；判断题答案只能是 TRUE 或 FALSE
                5. 题目数量由你根据资料丰富程度决定，不少于 10 道
                6. 每题必须能对应到某个片段编号
                输出 JSON：
                {"questions":[
                  {"type":"SA","difficulty":3,"stem":"题干","standardAnswer":"标准答案",
                   "explanation":"解析","sourceIndex":0},
                  {"type":"MC","difficulty":2,"stem":"题干","options":["A. x","B. y","C. z","D. w"],
                   "standardAnswer":"B","explanation":"解析","sourceIndex":1},
                  {"type":"TF","difficulty":2,"stem":"题干","standardAnswer":"TRUE",
                   "explanation":"解析","sourceIndex":2}
                ]}
                """);

        AiClient.ChatResult result = aiClient.chat("GENERATE", SYSTEM_GENERATE, prompt.toString(), 8000);
        JsonNode root = parseJson(result.content());
        JsonNode questions = root.path("questions");
        if (!questions.isArray()) {
            throw new BusinessException(3001, "模型未返回 questions 数组");
        }

        int saved = 0;
        int rejected = 0;
        for (JsonNode node : questions) {
            Question question = toQuestion(node, category, chunks);
            if (question == null) {
                rejected++;
                continue;
            }
            questionRepository.save(question);
            saved++;
        }
        log.info("分类出题完成: {} 入库={} 丢弃={}", category.name, saved, rejected);
        return saved;
    }

    private Question toQuestion(JsonNode node, Category category, List<Chunk> chunks) {
        String type = node.path("type").asString("").trim().toUpperCase();
        if (!List.of("SA", "MC", "TF").contains(type)) {
            return null;
        }
        String stem = node.path("stem").asString("").trim();
        String answer = node.path("standardAnswer").asString("").trim();
        String explanation = node.path("explanation").asString("").trim();
        int sourceIndex = node.path("sourceIndex").asInt(-1);

        // 三必填 + 来源，缺一拒绝入库
        if (stem.isEmpty() || answer.isEmpty() || explanation.isEmpty()) {
            return null;
        }
        if (sourceIndex < 0 || sourceIndex >= chunks.size()) {
            return null;
        }

        String optionsJson = null;
        if ("MC".equals(type)) {
            JsonNode options = node.path("options");
            if (!options.isArray() || options.size() < 2) {
                return null;
            }
            List<String> values = new ArrayList<>();
            options.forEach(option -> values.add(option.asString("")));
            try {
                optionsJson = objectMapper.writeValueAsString(values);
            } catch (RuntimeException e) {
                return null;
            }
        }
        if ("TF".equals(type)) {
            String upper = answer.toUpperCase();
            if (!"TRUE".equals(upper) && !"FALSE".equals(upper)) {
                return null;
            }
            answer = upper;
        }

        Question question = new Question();
        question.categoryId = category.id;
        question.type = type;
        question.difficulty = Math.min(Math.max(node.path("difficulty").asInt(3), 1), 5);
        question.stem = stem;
        question.optionsJson = optionsJson;
        question.standardAnswer = answer;
        question.explanation = explanation;
        question.sourceChunkId = chunks.get(sourceIndex).id;
        question.origin = "AI";
        question.qualityFlag = "OK";
        question.createdAt = Instant.now().toString();
        question.updatedAt = question.createdAt;
        return question;
    }

    private void progress(GenerateJob job, int done, int total, int questions, String message) {
        job.doneCategories = done;
        job.questionCount = questions;
        job.progress = total == 0 ? 90 : 45 + (int) (55.0 * done / total);
        job.message = "出题中 " + done + "/" + total + " · " + message;
        persist(job);
        emit(job, "progress");
    }

    // ------------------------------------------------------------
    // 查询 / SSE / 重置
    // ------------------------------------------------------------

    @Transactional(readOnly = true)
    public JobView view(String jobId) {
        GenerateJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException(2001, "任务不存在: " + jobId));
        return toView(job);
    }

    public SseEmitter stream(String jobId) {
        GenerateJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException(2001, "任务不存在: " + jobId));

        SseEmitter emitter = new SseEmitter(0L);
        subscribers.computeIfAbsent(jobId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(jobId, emitter));
        emitter.onTimeout(() -> removeEmitter(jobId, emitter));
        emitter.onError(error -> removeEmitter(jobId, emitter));

        try {
            emitter.send(SseEmitter.event().name("progress").data(toView(job)));
        } catch (Exception e) {
            removeEmitter(jobId, emitter);
            return emitter;
        }

        // 已结束的任务立刻补一帧终止事件并关闭，避免客户端一直挂着
        if ("DONE".equals(job.status) || "FAILED".equals(job.status)) {
            try {
                emitter.send(SseEmitter.event()
                        .name("DONE".equals(job.status) ? "done" : "error")
                        .data(toView(job)));
                emitter.complete();
            } catch (Exception e) {
                removeEmitter(jobId, emitter);
            }
            return emitter;
        }

        if ("PENDING".equals(job.status) || "RUNNING".equals(job.status)) {
            start(jobId);
        }
        return emitter;
    }

    @Transactional
    public ResetResult reset(boolean keepCategories) {
        Long before = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM question", Long.class);
        jdbcTemplate.update("DELETE FROM question");
        jdbcTemplate.update("DELETE FROM mastery");
        jdbcTemplate.update("DELETE FROM wrong_book");
        jdbcTemplate.update("DELETE FROM practice_item");
        jdbcTemplate.update("DELETE FROM exam_item");
        if (!keepCategories) {
            jdbcTemplate.update("UPDATE chunk SET category_id = NULL");
            jdbcTemplate.update("DELETE FROM category");
        }
        Long usage = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_usage", Long.class);
        log.info("题库已重置: 删除题目 {} 条，ai_usage 保留 {} 行", before, usage);
        return new ResetResult(before == null ? 0 : before, usage == null ? 0 : usage);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary() {
        Map<String, Object> body = new HashMap<>();
        body.put("questions", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM question", Long.class));
        body.put("categories", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM category", Long.class));
        body.put("documents", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM document", Long.class));
        body.put("chunks", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chunk", Long.class));
        body.put("unclassifiedChunks",
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chunk WHERE category_id IS NULL", Long.class));
        body.put("byType", jdbcTemplate.queryForList(
                "SELECT type, COUNT(*) AS cnt FROM question GROUP BY type"));
        return body;
    }

    private void emit(GenerateJob job, String event) {
        List<SseEmitter> list = subscribers.get(job.id);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(event).data(toView(job)));
            } catch (Exception e) {
                removeEmitter(job.id, emitter);
            }
        }
        if ("done".equals(event) || "error".equals(event)) {
            completeEmitters(job.id);
        }
    }

    private void completeEmitters(String jobId) {
        List<SseEmitter> list = subscribers.remove(jobId);
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // 客户端已断开
            }
        }
    }

    private void removeEmitter(String jobId, SseEmitter emitter) {
        List<SseEmitter> list = subscribers.get(jobId);
        if (list != null) {
            list.remove(emitter);
        }
    }

    private JobView toView(GenerateJob job) {
        return new JobView(job.id, job.status, job.stage, job.progress, job.totalChunks,
                job.totalCategories, job.doneCategories, job.questionCount, job.estimatedCost, job.message);
    }

    private void persist(GenerateJob job) {
        job.updatedAt = Instant.now().toString();
        jobRepository.save(job);
    }

    private int readMaxChunks(GenerateJob job) {
        try {
            if (job.paramsJson != null) {
                return objectMapper.readTree(job.paramsJson).path("maxChunks").asInt(DEFAULT_MAX_CHUNKS);
            }
        } catch (RuntimeException ignored) {
            // 参数损坏时用默认值
        }
        return DEFAULT_MAX_CHUNKS;
    }

    private JsonNode parseJson(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(3001, "模型返回为空");
        }
        String cleaned = content.trim();
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            int lastFence = cleaned.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                cleaned = cleaned.substring(firstNewline + 1, lastFence).trim();
            }
        }
        try {
            return objectMapper.readTree(cleaned);
        } catch (RuntimeException e) {
            throw new BusinessException(3001, "模型返回的 JSON 无法解析");
        }
    }

    private static double round6(double value) {
        return Math.round(value * 1_000_000d) / 1_000_000d;
    }
}
