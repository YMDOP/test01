package com.love.interviewforge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 题目。三必填：标准答案、解析、来源切片。 */
@Entity
@Table(name = "question")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "category_id", nullable = false)
    public Long categoryId;

    @Column(nullable = false, length = 8)
    public String type;

    @Column(nullable = false)
    public int difficulty = 3;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String stem;

    @Column(name = "options_json", columnDefinition = "TEXT")
    public String optionsJson;

    @Column(name = "standard_answer", nullable = false, columnDefinition = "TEXT")
    public String standardAnswer;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String explanation;

    @Column(name = "source_chunk_id", nullable = false)
    public Long sourceChunkId;

    @Column(nullable = false, length = 16)
    public String origin = "AI";

    @Column(name = "quality_flag", nullable = false, length = 24)
    public String qualityFlag = "OK";

    @Column(name = "created_at", nullable = false, length = 40)
    public String createdAt;

    @Column(name = "updated_at", nullable = false, length = 40)
    public String updatedAt;
}
