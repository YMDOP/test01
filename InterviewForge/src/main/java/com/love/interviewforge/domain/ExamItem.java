package com.love.interviewforge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 模拟卷中的一道题。 */
@Entity
@Table(name = "exam_item")
public class ExamItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "exam_id", nullable = false)
    public Long examId;

    @Column(name = "question_id", nullable = false)
    public Long questionId;

    @Column(name = "order_no", nullable = false)
    public int orderNo;

    @Column(name = "user_answer", columnDefinition = "TEXT")
    public String userAnswer;

    public Double score;

    @Column(name = "max_score")
    public Double maxScore;

    /** PENDING / OK / FAILED */
    @Column(nullable = false, length = 16)
    public String judged = "PENDING";

    @Column(name = "ai_feedback_json", columnDefinition = "TEXT")
    public String aiFeedbackJson;

    @Column(name = "judged_at", length = 40)
    public String judgedAt;
}
