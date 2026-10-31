package com.love.interviewforge.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamItemRepository extends JpaRepository<ExamItem, Long> {
    List<ExamItem> findByExamIdOrderByOrderNoAsc(Long examId);
}
