package com.love.interviewforge.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WrongBookRepository extends JpaRepository<WrongBook, Long> {
    Optional<WrongBook> findByQuestionId(Long questionId);

    List<WrongBook> findByResolvedAtIsNullOrderByIdDesc();
}
