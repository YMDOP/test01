package com.love.interviewforge.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GenerateJobStepRepository extends JpaRepository<GenerateJobStep, Long> {
    List<GenerateJobStep> findByJobId(String jobId);

    Optional<GenerateJobStep> findByJobIdAndCategoryId(String jobId, Long categoryId);
}
