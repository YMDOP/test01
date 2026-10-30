package com.love.interviewforge.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChunkRepository extends JpaRepository<Chunk, Long> {

    List<Chunk> findByDocumentIdOrderByOrdinal(Long documentId);

    List<Chunk> findByCategoryIdIsNullOrderByIdAsc();

    List<Chunk> findByCategoryIdOrderByIdAsc(Long categoryId);

    long countByCategoryIdIsNull();
}
