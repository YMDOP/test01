package com.love.interviewforge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 掌握度：每道题一行，没有行视为 UNTRIED。 */
@Entity
@Table(name = "mastery")
public class Mastery {

    @Id
    @Column(name = "question_id", nullable = false)
    public Long questionId;

    /** UNTRIED / CORRECT / WRONG / VAGUE */
    @Column(nullable = false, length = 16)
    public String status = "UNTRIED";

    @Column(name = "wrong_count", nullable = false)
    public int wrongCount;

    @Column(name = "correct_count", nullable = false)
    public int correctCount;

    @Column(name = "last_at", length = 40)
    public String lastAt;
}
