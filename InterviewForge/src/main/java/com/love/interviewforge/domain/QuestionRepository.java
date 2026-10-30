package com.love.interviewforge.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    long countByCategoryId(Long categoryId);
}
