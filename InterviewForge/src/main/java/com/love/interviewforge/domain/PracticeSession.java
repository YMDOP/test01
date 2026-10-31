package com.love.interviewforge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 练习会话。 */
@Entity
@Table(name = "practice_session")
public class PracticeSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "params_json", columnDefinition = "TEXT")
    public String paramsJson;

    @Column(name = "started_at", nullable = false, length = 40)
    public String startedAt;

    @Column(name = "ended_at", length = 40)
    public String endedAt;

    @Column(nullable = false)
    public int total;

    @Column(nullable = false)
    public int known;

    @Column(nullable = false)
    public int unknown;

    @Column(nullable = false)
    public int vague;
}
