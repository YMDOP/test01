package com.love.interviewforge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 知识点分类。 */
@Entity
@Table(name = "category")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 64)
    public String name;

    @Column(columnDefinition = "TEXT")
    public String description;

    /** 为空表示一级分类；否则指向父分类。 */
    @Column(name = "parent_id")
    public Long parentId;

    @Column(name = "sort_order", nullable = false)
    public int sortOrder;

    @Column(nullable = false, length = 16)
    public String origin = "AI";

    @Column(name = "created_at", nullable = false, length = 40)
    public String createdAt;
}
