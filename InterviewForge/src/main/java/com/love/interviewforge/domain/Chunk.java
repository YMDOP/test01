package com.love.interviewforge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 文本切片，出题的最小原文单位。 */
@Entity
@Table(name = "chunk")
public class Chunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "document_id", nullable = false)
    public Long documentId;

    @Column(nullable = false)
    public int ordinal;

    @Column(name = "heading_path", length = 512)
    public String headingPath;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String text;

    @Column(name = "char_count", nullable = false)
    public int charCount;

    @Column(length = 64)
    public String fingerprint;

    @Column(name = "category_id")
    public Long categoryId;
}
