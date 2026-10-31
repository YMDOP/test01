package com.love.interviewforge.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
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
 * 题库生成流水线，三阶段：
 *
 * <pre>
 * TAXONOMY  依据文档清单归纳两级分类树（一级领域 / 二级主题）
 * ASSIGN    把切片归入「已确定」的二级分类，AI 只能选不能造
 * GENERATE  每个二级分类各出一批题，保证每个分类都有题
 * </pre>
 *
 * <p>任务状态持久化在 generate_job / generate_job_step，进程重启后自动续跑。</p>
 */
@Service
public class GenerateService {

    private static final Logger log = LoggerFactory.getLogger(GenerateService.class);

    private static final int ASSIGN_BATCH = 30;
    private static final int MAX_CHUNKS_PER_CATEGORY = 30;
    private static final int MAX_LEAVES_FOR_GENERATE = 20;
    private static final double CONFIRM_THRESHOLD = 5.0d;
    private static final int DEFAULT_MAX_CHUNKS = 300;

    private static final String SYSTEM_TAXONOMY =
            "你是 Java 后端求职资料的整理专家。只输出 JSON，不要解释。";
    private static final String SYSTEM_ASSIGN =
            "你是资料归类助手。只能从给定的分类中选择，不得创造新分类。只输出 JSON。";
    private static final String SYSTEM_GENERATE =
            "你是拥有 5 年经验的 Java 后端面试官，面向校招候选人出题。只输出 JSON，不要解释。";

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
        int chunkCount = (int) Math.min(budget, unclassified > 0 ? unclassified : chunkRepository.count());
        int assignCalls = (int) Math.ceil(chunkCount / (double) ASSIGN_BATCH);
        int calls = 1 + assignCalls + Math.min(MAX_LEAVES_FOR_GENERATE, 12);

        PricingService.Price price = pricingService.priceFor("deepseek-chat");
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
        job.stage = "TAXONOMY";
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

    @EventListener(ApplicationReadyEvent.class)
    public void resumePendingJobs() {
        for (GenerateJob job : jobRepository.findByStatusIn(List.of("RUNNING"))) {
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

            if (job.stage == null || "TAXONOMY".equals(job.stage)) {
                buildTaxonomy(job);
                job.stage = "ASSIGN";
                persist(job);
            }
            if ("ASSIGN".equals(job.stage)) {
                assignChunks(job);
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

    // ------------------------------------------------------------
    // 阶段一：归纳两级分类树
    // ------------------------------------------------------------

    private void buildTaxonomy(GenerateJob job) {
        List<Map<String, Object>> docs = jdbcTemplate.queryForList("""
                SELECT d.id,
                       d.filename,
                       d.path,
                       (SELECT c.heading_path FROM chunk c
                         WHERE c.document_id = d.id AND c.heading_path IS NOT NULL AND c.heading_path <> ''
                         ORDER BY c.ordinal LIMIT 1) AS heading
                  FROM document d
                 ORDER BY d.id
                """);
        if (docs.isEmpty()) {
            throw new BusinessException(2001, "还没有导入文档");
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("下面是 Java 后端求职笔记库的文件清单（编号、文件名、首个章节）。\n")
                .append("请归纳成两级分类树。\n\n");
        for (int i = 0; i < docs.size(); i++) {
            Map<String, Object> doc = docs.get(i);
            prompt.append(i).append(". ")
                    .append(doc.get("filename"));
            Object heading = doc.get("heading");
            if (heading != null && !String.valueOf(heading).isBlank()) {
                prompt.append(" ｜ 章节: ").append(String.valueOf(heading));
            }
            prompt.append('\n');
        }
        prompt.append("""
                
                要求：
                1. 一级分类 3 到 6 个，命名参考：「Java 八股」「项目实战」「实习经历」「面试准备与复盘」「算法刷题」
                2. 每个一级下 2 到 8 个二级分类，二级必须具体、能直接出题
                3. 二级分类总数 12 到 20 个，每个二级至少覆盖 2 个文件
                4. 项目类二级用项目名（如「墨韵文书项目」「AI Doc 项目」）
                   实习类二级用公司名（如「北京青蓝致远」「天津经济数字产业集团」）
                5. 一级分类「Java 八股」的二级用技术主题（如「多线程」「集合」「JVM」「MySQL」），不要合并成一个「Java 基础」
                6. 只输出 JSON：
                {"tree":[{"name":"Java 八股","children":["多线程","JVM"]}],
                 "documents":[{"index":0,"parent":"Java 八股","child":"多线程"}]}
                """);

        AiClient.ChatResult result = aiClient.chat("CLASSIFY", SYSTEM_TAXONOMY, prompt.toString(), 3000);
        JsonNode root = parseJson(result.content());
        JsonNode tree = root.path("tree");
        if (!tree.isArray() || tree.isEmpty()) {
            throw new BusinessException(3001, "模型未返回分类树");
        }

        int parents = 0;
        int leaves = 0;
        for (JsonNode node : tree) {
            String parentName = node.path("name").asString("").trim();
            if (parentName.isEmpty()) {
                continue;
            }
            Category parent = findOrCreate(parentName, null, "领域");
            parents++;
            JsonNode children = node.path("children");
            if (!children.isArray()) {
                continue;
            }
            for (JsonNode childNode : children) {
                String childName = childNode.asString("").trim();
                if (childName.isEmpty()) {
                    continue;
                }
                createLeaf(parent, childName);
                leaves++;
            }
        }
        log.info("分类树已建立: 一级 {} 个，二级 {} 个", parents, leaves);

        job.message = "分类树：" + parents + " 个领域 / " + leaves + " 个主题";
        job.progress = 15;
        persist(job);
        emit(job, "progress");
    }

    private Category findOrCreate(String name, Long parentId, String description) {
        return categoryRepository.findByName(name).orElseGet(() -> {
            Category created = new Category();
            created.name = name;
            created.description = description;
            created.parentId = parentId;
            created.origin = "AI";
            created.createdAt = Instant.now().toString();
            return categoryRepository.save(created);
        });
    }

    private void createLeaf(Category parent, String childName) {
        String qualified = parent.name + " / " + childName;
        if (categoryRepository.findByName(qualified).isPresent()) {
            return;
        }
        // 二级重名时用「父 / 子」作为展示名，避免唯一约束冲突
        String name = categoryRepository.findByName(childName).isPresent() ? qualified : childName;
        Category leaf = new Category();
        leaf.name = name;
        leaf.description = parent.name;
        leaf.parentId = parent.id;
        leaf.origin = "AI";
        leaf.createdAt = Instant.now().toString();
        categoryRepository.save(leaf);
    }

    // ------------------------------------------------------------
    // 阶段二：把切片归入已有分类
    // ------------------------------------------------------------

    private void assignChunks(GenerateJob job) {
        List<Category> leaves = categoryRepository.findByParentIdIsNotNullOrderByIdAsc();
        if (leaves.isEmpty()) {
            throw new BusinessException(2001, "没有可用的二级分类");
        }
        Map<String, Category> byLabel = new LinkedHashMap<>();
        Map<Long, String> labelOf = new HashMap<>();
        for (Category leaf : leaves) {
            String parent = categoryRepository.findById(leaf.parentId).map(c -> c.name).orElse("");
            String label = parent + " / " + leaf.name;
            byLabel.put(label, leaf);
            labelOf.put(leaf.id, label);
        }

        int budget = readMaxChunks(job);
        List<Chunk> chunks = chunkRepository.findByCategoryIdIsNullOrderByIdAsc();
        if (chunks.size() > budget) {
            chunks = chunks.subList(0, budget);
        }
        job.totalChunks = chunks.size();
        persist(job);

        String categoryList = String.join("\n", byLabel.keySet());
        int processed = 0;
        int failures = 0;

        for (int start = 0; start < chunks.size(); start += ASSIGN_BATCH) {
            List<Chunk> batch = chunks.subList(start, Math.min(start + ASSIGN_BATCH, chunks.size()));
            try {
                assignBatch(batch, byLabel, categoryList);
            } catch (RuntimeException e) {
                failures++;
                log.warn("归类批次失败，跳过: start={} reason={}", start, e.getMessage());
            }
            processed += batch.size();
            job.progress = 15 + (int) (35.0 * processed / Math.max(1, chunks.size()));
            job.message = "归类中 " + Math.min(processed, chunks.size()) + "/" + chunks.size()
                    + (failures > 0 ? "（失败批次 " + failures + "）" : "");
            persist(job);
            emit(job, "progress");
        }

        int rescued = rescueEmptyLeaves();
        if (rescued > 0) {
            job.message = "归类完成，兜底补齐 " + rescued + " 个分类";
            persist(job);
        }
    }

    /**
     * 兜底：某个二级分类一条切片都没分到，就按分类名在正文里做关键词回捞，
     * 避免「天津经济数字产业集团」这类只出现在正文里的分类被丢空。
     */
    private int rescueEmptyLeaves() {
        int rescued = 0;
        for (Category leaf : categoryRepository.findByParentIdIsNotNullOrderByIdAsc()) {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM chunk WHERE category_id = ?", Long.class, leaf.id);
            if (count != null && count > 0) {
                continue;
            }
            String key = leaf.name.length() > 6 ? leaf.name.substring(0, 6) : leaf.name;
            List<Long> ids = jdbcTemplate.queryForList("""
                    SELECT id FROM chunk WHERE text LIKE ?
                     ORDER BY CASE WHEN category_id IS NULL THEN 0 ELSE 1 END, id
                     LIMIT 10
                    """, Long.class, "%" + key + "%");
            for (Long id : ids) {
                jdbcTemplate.update("UPDATE chunk SET category_id = ? WHERE id = ?", leaf.id, id);
            }
            if (!ids.isEmpty()) {
                rescued++;
                log.info("兜底归类: {} 回捞 {} 个切片", leaf.name, ids.size());
            }
        }
        return rescued;
    }

    private void assignBatch(List<Chunk> batch, Map<String, Category> byLabel, String categoryList) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("可选分类（只能从中选择，不得新建）：\n").append(categoryList).append("\n\n");
        for (int i = 0; i < batch.size(); i++) {
            prompt.append("### 片段 ").append(i).append('\n')
                    .append(batch.get(i).text).append("\n\n");
        }
        prompt.append("""
                请为每个片段选择最贴切的一个分类（用「一级 / 二级」完整路径）。
                输出 JSON：{"assignments":[{"index":0,"category":"Java 八股 / 多线程"}]}
                """);

        AiClient.ChatResult result = aiClient.chat("CLASSIFY", SYSTEM_ASSIGN, prompt.toString(), 3000);
        JsonNode root = parseJson(result.content());
        JsonNode assignments = root.path("assignments");
        if (!assignments.isArray()) {
            throw new BusinessException(3001, "模型未返回 assignments 数组");
        }
        for (JsonNode node : assignments) {
            int index = node.path("index").asInt(-1);
            String label = node.path("category").asString("").trim();
            if (index < 0 || index >= batch.size()) {
                continue;
            }
            Category category = byLabel.get(label);
            if (category == null) {
                continue;
            }
            Chunk chunk = batch.get(index);
            chunk.categoryId = category.id;
            chunkRepository.save(chunk);
        }
    }

    // ------------------------------------------------------------
    // 阶段三：按二级分类出题
    // ------------------------------------------------------------

    private void generate(GenerateJob job) {
        Map<Long, Long> chunkCounts = new HashMap<>();
        jdbcTemplate.query(
                "SELECT category_id, COUNT(*) AS c FROM chunk WHERE category_id IS NOT NULL GROUP BY category_id",
                (RowCallbackHandler) rs -> chunkCounts.put(rs.getLong("category_id"), rs.getLong("c")));

        // 没分到切片的分类直接删掉，保证「每个分类都有题」
        List<Category> leaves = categoryRepository.findByParentIdIsNotNullOrderByIdAsc();
        for (Category leaf : leaves) {
            if (chunkCounts.getOrDefault(leaf.id, 0L) == 0) {
                categoryRepository.delete(leaf);
            }
        }
        for (Category parent : categoryRepository.findByParentIdIsNullOrderByIdAsc()) {
            boolean hasChild = categoryRepository.findByParentIdIsNotNullOrderByIdAsc().stream()
                    .anyMatch(child -> parent.id.equals(child.parentId));
            if (!hasChild) {
                categoryRepository.delete(parent);
            }
        }

        List<Category> ranked = categoryRepository.findByParentIdIsNotNullOrderByIdAsc().stream()
                .filter(leaf -> chunkCounts.getOrDefault(leaf.id, 0L) > 0)
                .sorted(Comparator.comparingLong((Category leaf) -> chunkCounts.getOrDefault(leaf.id, 0L)).reversed())
                .toList();
        List<Category> categories = ranked.stream().limit(MAX_LEAVES_FOR_GENERATE).toList();

        job.totalCategories = categories.size();
        job.message = "出题分类 " + categories.size() + " 个";
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
                int generated = generateCategory(category);
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

        // 出题失败或 0 题的分类重试一次；仍为 0 则删除，保证「有分类必有题」
        int retried = 0;
        for (Category category : categories) {
            if (questionRepository.countByCategoryId(category.id) > 0 || retried >= 3) {
                continue;
            }
            retried++;
            try {
                int generated = generateCategory(category);
                totalQuestions += generated;
                if (generated > 0) {
                    job.message = "重试补齐 " + category.name + "（" + generated + " 题）";
                    persist(job);
                }
            } catch (RuntimeException e) {
                log.warn("重试出题仍失败: {} - {}", category.name, e.getMessage());
            }
        }

        for (Category category : categories) {
            if (questionRepository.countByCategoryId(category.id) == 0) {
                stepRepository.findByJobIdAndCategoryId(job.id, category.id).ifPresent(stepRepository::delete);
                categoryRepository.delete(category);
                log.info("删除无题目的分类: {}", category.name);
            }
        }
        for (Category parent : categoryRepository.findByParentIdIsNullOrderByIdAsc()) {
            boolean hasChild = categoryRepository.findByParentIdIsNotNullOrderByIdAsc().stream()
                    .anyMatch(child -> parent.id.equals(child.parentId));
            if (!hasChild) {
                categoryRepository.delete(parent);
            }
        }

        job.questionCount = totalQuestions;
        persist(job);
    }

    private int generateCategory(Category category) {
        List<Chunk> chunks = chunkRepository.findByCategoryIdOrderByIdAsc(category.id).stream()
                .limit(MAX_CHUNKS_PER_CATEGORY)
                .toList();
        String parent = category.parentId == null ? ""
                : categoryRepository.findById(category.parentId).map(c -> c.name).orElse("");

        StringBuilder prompt = new StringBuilder();
        prompt.append("【一级分类】").append(parent).append('\n')
                .append("【二级分类】").append(category.name).append("\n\n【参考资料】\n");
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
                5. 每个分类不少于 10 道题
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
        job.progress = total == 0 ? 90 : 50 + (int) (50.0 * done / total);
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

    /** 重置前把数据库复制到 data/backup/，避免误删无法恢复。 */
    private void backupDatabase() {
        Path dataDir = com.love.interviewforge.config.AppPaths.resolve();
        Path db = dataDir.resolve("interviewforge.db");
        if (!Files.exists(db)) {
            return;
        }
        // WAL 模式下必须先 checkpoint，否则只拷主文件会丢掉最近写入
        try {
            jdbcTemplate.execute("PRAGMA wal_checkpoint(TRUNCATE)");
        } catch (RuntimeException e) {
            log.warn("备份前 checkpoint 失败: {}", e.getMessage());
        }
        try {
            Path backupDir = dataDir.resolve("backup");
            Files.createDirectories(backupDir);
            String stamp = LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path target = backupDir.resolve("interviewforge-" + stamp + ".db");
            Files.copy(db, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("重置前已备份数据库: {}", target);
        } catch (IOException e) {
            // 备份失败不阻断重置，但必须留下警告
            log.warn("重置前备份数据库失败: {}", e.getMessage());
        }
    }

    @Transactional
    public ResetResult reset(boolean keepCategories) {
        backupDatabase();
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

    /** 分类树，供前端渲染。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> tree() {
        List<Category> all = categoryRepository.findAll();
        Map<Long, Category> parents = new LinkedHashMap<>();
        for (Category category : all) {
            if (category.parentId == null) {
                parents.put(category.id, category);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Category parent : parents.values()) {
            List<Map<String, Object>> children = new ArrayList<>();
            for (Category child : all) {
                if (parent.id.equals(child.parentId)) {
                    Map<String, Object> node = new LinkedHashMap<>();
                    node.put("id", child.id);
                    node.put("name", child.name);
                    node.put("questionCount",
                            jdbcTemplate.queryForObject(
                                    "SELECT COUNT(*) FROM question WHERE category_id = ?", Long.class, child.id));
                    children.add(node);
                }
            }
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", parent.id);
            node.put("name", parent.name);
            node.put("children", children);
            result.add(node);
        }
        return result;
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
