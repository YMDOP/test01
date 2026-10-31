package com.love.interviewforge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 练习会话中的一道题；selfRating 为 PENDING 表示还没自评。 */
@Entity
@Table(name = "practice_item")
public class PracticeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "session_id", nullable = false)
    public Long sessionId;

    @Column(name = "question_id", nullable = false)
    public Long questionId;

    @Column(name = "self_rating", nullable = false, length = 16)
    public String selfRating = "PENDING";

    @Column(name = "answered_at", nullable = false, length = 40)
    public String answeredAt;
}
