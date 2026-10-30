package com.love.interviewforge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 任务按分类粒度的进度，用于断点续跑。 */
@Entity
@Table(name = "generate_job_step")
public class GenerateJobStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "job_id", nullable = false, length = 40)
    public String jobId;

    @Column(name = "category_id")
    public Long categoryId;

    @Column(name = "category_name", length = 64)
    public String categoryName;

    /** PENDING / DONE / FAILED */
    @Column(nullable = false, length = 16)
    public String status = "PENDING";

    @Column(name = "question_count", nullable = false)
    public int questionCount;

    @Column(columnDefinition = "TEXT")
    public String error;

    @Column(name = "updated_at", nullable = false, length = 40)
    public String updatedAt;
}
