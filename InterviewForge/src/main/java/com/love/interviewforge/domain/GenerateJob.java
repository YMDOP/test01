package com.love.interviewforge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 生成任务主记录。id 用 UUID 字符串，便于前端拿着轮询/重连。 */
@Entity
@Table(name = "generate_job")
public class GenerateJob {

    @Id
    @Column(length = 40)
    public String id;

    /** PENDING / RUNNING / DONE / FAILED */
    @Column(nullable = false, length = 16)
    public String status = "PENDING";

    /** CLASSIFY / GENERATE / FINISHED */
    @Column(length = 16)
    public String stage;

    @Column(name = "params_json", columnDefinition = "TEXT")
    public String paramsJson;

    @Column(nullable = false)
    public int progress;

    @Column(name = "total_chunks", nullable = false)
    public int totalChunks;

    @Column(name = "total_categories", nullable = false)
    public int totalCategories;

    @Column(name = "done_categories", nullable = false)
    public int doneCategories;

    @Column(name = "question_count", nullable = false)
    public int questionCount;

    @Column(name = "estimated_cost", nullable = false)
    public double estimatedCost;

    @Column(columnDefinition = "TEXT")
    public String message;

    @Column(name = "stats_json", columnDefinition = "TEXT")
    public String statsJson;

    @Column(name = "created_at", nullable = false, length = 40)
    public String createdAt;

    @Column(name = "updated_at", nullable = false, length = 40)
    public String updatedAt;
}
