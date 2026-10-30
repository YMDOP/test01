package com.love.interviewforge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 导入的文档。只记路径，不复制原文件。 */
@Entity
@Table(name = "document")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 1024)
    public String path;

    @Column(nullable = false, length = 256)
    public String filename;

    @Column(nullable = false, length = 16)
    public String ext;

    @Column(name = "size", nullable = false)
    public long size;

    @Column(length = 64)
    public String sha256;

    @Column(name = "imported_at", nullable = false, length = 40)
    public String importedAt;

    @Column(name = "parse_status", nullable = false, length = 16)
    public String parseStatus = "PENDING";

    @Column(name = "parse_error", columnDefinition = "TEXT")
    public String parseError;

    @Column(name = "chunk_count", nullable = false)
    public int chunkCount;
}
