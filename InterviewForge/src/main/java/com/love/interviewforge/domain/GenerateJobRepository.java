package com.love.interviewforge.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GenerateJobRepository extends JpaRepository<GenerateJob, String> {
    List<GenerateJob> findByStatusIn(List<String> statuses);
}
