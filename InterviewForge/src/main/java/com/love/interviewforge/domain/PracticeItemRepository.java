package com.love.interviewforge.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticeItemRepository extends JpaRepository<PracticeItem, Long> {
    List<PracticeItem> findBySessionIdOrderByIdAsc(Long sessionId);

    Optional<PracticeItem> findBySessionIdAndQuestionId(Long sessionId, Long questionId);

    void deleteBySessionId(Long sessionId);
}
