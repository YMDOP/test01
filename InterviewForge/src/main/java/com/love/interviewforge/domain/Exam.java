package com.love.interviewforge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 模拟面试卷。 */
@Entity
@Table(name = "exam")
public class Exam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** RUNNING / SUBMITTED / ABANDONED */
    @Column(nullable = false, length = 16)
    public String status = "RUNNING";

    @Column(name = "blueprint_json", columnDefinition = "TEXT")
    public String blueprintJson;

    @Column(name = "started_at", nullable = false, length = 40)
    public String startedAt;

    @Column(name = "submitted_at", length = 40)
    public String submittedAt;

    @Column(name = "duration_sec")
    public Long durationSec;

    public Double score;

    @Column(name = "max_score")
    public Double maxScore;
}
