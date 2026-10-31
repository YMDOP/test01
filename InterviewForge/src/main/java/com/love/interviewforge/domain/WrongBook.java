package com.love.interviewforge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 错题本，按题去重。 */
@Entity
@Table(name = "wrong_book")
public class WrongBook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "question_id", nullable = false, unique = true)
    public Long questionId;

    /** PRACTICE / EXAM */
    @Column(name = "source_type", nullable = false, length = 16)
    public String sourceType;

    @Column(name = "source_id")
    public Long sourceId;

    @Column(name = "created_at", nullable = false, length = 40)
    public String createdAt;

    @Column(name = "resolved_at", length = 40)
    public String resolvedAt;
}
