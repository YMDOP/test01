package com.love.interviewforge.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.love.interviewforge.common.BusinessException;
import com.love.interviewforge.domain.Mastery;
import com.love.interviewforge.domain.MasteryRepository;
import com.love.interviewforge.domain.PracticeItem;
import com.love.interviewforge.domain.PracticeItemRepository;
import com.love.interviewforge.domain.PracticeSession;
import com.love.interviewforge.domain.PracticeSessionRepository;
import com.love.interviewforge.domain.WrongBook;
import com.love.interviewforge.domain.WrongBookRepository;

/**
 * 练习闭环：抽题 → 自评 → 掌握度写回 → 小结。
 *
 * <p>整条链路不调用任何 AI，也不写 ai_usage。</p>
 */
@Service
public class PracticeService {

    private static final List<String> RATINGS = List.of("KNOWN", "UNKNOWN", "VAGUE");

    private final PickService pickService;
    private final PracticeSessionRepository sessionRepository;
    private final PracticeItemRepository itemRepository;
    private final MasteryRepository masteryRepository;
    private final WrongBookRepository wrongBookRepository;

    public PracticeService(PickService pickService,
                           PracticeSessionRepository sessionRepository,
                           PracticeItemRepository itemRepository,
                           MasteryRepository masteryRepository,
                           WrongBookRepository wrongBookRepository) {
        this.pickService = pickService;
        this.sessionRepository = sessionRepository;
        this.itemRepository = itemRepository;
        this.masteryRepository = masteryRepository;
        this.wrongBookRepository = wrongBookRepository;
    }

    public record PracticeView(Long sessionId, String status, List<QuestionService.QuestionView> items,
                               int total, int answered) {
    }

    public record RateResult(Long questionId, String masteryStatus, int answered, int total,
                             int known, int unknown, int vague) {
    }

    public record Summary(Long sessionId, int total, int known, int unknown, int vague, long elapsedSeconds) {
    }

    @Transactional
    public PracticeView start(PickService.PickRequest request) {
        PickService.PickResult picked = pickService.pick(request);
        if (picked.items().isEmpty()) {
            throw new BusinessException(2001, "没有符合条件的题目，请先导入文档并生成题库");
        }

        String now = Instant.now().toString();
        PracticeSession session = new PracticeSession();
        session.paramsJson = "{}";
        session.startedAt = now;
        session.total = picked.items().size();
        session = sessionRepository.save(session);

        for (QuestionService.QuestionView item : picked.items()) {
            PracticeItem practiceItem = new PracticeItem();
            practiceItem.sessionId = session.id;
            practiceItem.questionId = item.id();
            practiceItem.selfRating = "PENDING";
            practiceItem.answeredAt = now;
            itemRepository.save(practiceItem);
        }

        return new PracticeView(session.id, "IN_PROGRESS", picked.items(), picked.items().size(), 0);
    }

    @Transactional
    public RateResult rate(Long sessionId, Long questionId, String rating) {
        String normalized = rating == null ? "" : rating.trim().toUpperCase();
        if (!RATINGS.contains(normalized)) {
            throw new BusinessException(1002, "自评只能是 KNOWN / UNKNOWN / VAGUE");
        }
        PracticeSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(2001, "练习会话不存在: " + sessionId));
        PracticeItem item = itemRepository.findBySessionIdAndQuestionId(sessionId, questionId)
                .orElseThrow(() -> new BusinessException(2001, "该题不在本次会话中"));

        item.selfRating = normalized;
        item.answeredAt = Instant.now().toString();
        itemRepository.save(item);

        applyMastery(questionId, normalized, sessionId);
        applyWrongBook(questionId, normalized, sessionId);

        List<PracticeItem> items = itemRepository.findBySessionIdOrderByIdAsc(sessionId);
        int known = count(items, "KNOWN");
        int unknown = count(items, "UNKNOWN");
        int vague = count(items, "VAGUE");
        int answered = known + unknown + vague;

        session.known = known;
        session.unknown = unknown;
        session.vague = vague;
        sessionRepository.save(session);

        String masteryStatus = switch (normalized) {
            case "KNOWN" -> "CORRECT";
            case "UNKNOWN" -> "WRONG";
            default -> "VAGUE";
        };
        return new RateResult(questionId, masteryStatus, answered, items.size(), known, unknown, vague);
    }

    private void applyMastery(Long questionId, String rating, Long sessionId) {
        Mastery mastery = masteryRepository.findById(questionId).orElseGet(() -> {
            Mastery created = new Mastery();
            created.questionId = questionId;
            created.status = "UNTRIED";
            return created;
        });
        mastery.lastAt = Instant.now().toString();
        switch (rating) {
            case "KNOWN" -> {
                mastery.status = "CORRECT";
                mastery.correctCount++;
            }
            case "UNKNOWN" -> {
                mastery.status = "WRONG";
                mastery.wrongCount++;
            }
            default -> mastery.status = "VAGUE";
        }
        masteryRepository.save(mastery);
    }

    private void applyWrongBook(Long questionId, String rating, Long sessionId) {
        WrongBook existing = wrongBookRepository.findByQuestionId(questionId).orElse(null);
        if ("UNKNOWN".equals(rating)) {
            if (existing == null) {
                WrongBook entry = new WrongBook();
                entry.questionId = questionId;
                entry.sourceType = "PRACTICE";
                entry.sourceId = sessionId;
                entry.createdAt = Instant.now().toString();
                wrongBookRepository.save(entry);
            } else if (existing.resolvedAt != null) {
                existing.resolvedAt = null;
                wrongBookRepository.save(existing);
            }
            return;
        }
        // 记为「会」时关闭错题，但保留历史
        if (existing != null && existing.resolvedAt == null) {
            existing.resolvedAt = Instant.now().toString();
            wrongBookRepository.save(existing);
        }
    }

    @Transactional
    public Summary finish(Long sessionId) {
        PracticeSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(2001, "练习会话不存在: " + sessionId));
        List<PracticeItem> items = itemRepository.findBySessionIdOrderByIdAsc(sessionId);

        session.known = count(items, "KNOWN");
        session.unknown = count(items, "UNKNOWN");
        session.vague = count(items, "VAGUE");
        session.endedAt = Instant.now().toString();
        sessionRepository.save(session);

        long seconds = 0;
        try {
            seconds = Duration.between(Instant.parse(session.startedAt), Instant.parse(session.endedAt)).getSeconds();
        } catch (RuntimeException ignored) {
            // 时间戳异常不影响结果
        }
        return new Summary(session.id, items.size(), session.known, session.unknown, session.vague, seconds);
    }

    @Transactional(readOnly = true)
    public PracticeView get(Long sessionId) {
        PracticeSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(2001, "练习会话不存在: " + sessionId));
        List<PracticeItem> items = itemRepository.findBySessionIdOrderByIdAsc(sessionId);
        List<QuestionService.QuestionView> views = new ArrayList<>();
        int answered = 0;
        List<Long> ids = new ArrayList<>();
        for (PracticeItem item : items) {
            if (!"PENDING".equals(item.selfRating)) {
                answered++;
            }
            ids.add(item.questionId);
        }
        views.addAll(pickService.pickByIds(ids));
        String status = session.endedAt == null ? "IN_PROGRESS" : "FINISHED";
        return new PracticeView(session.id, status, views, items.size(), answered);
    }

    private int count(List<PracticeItem> items, String rating) {
        return (int) items.stream().filter(item -> rating.equals(item.selfRating)).count();
    }
}
