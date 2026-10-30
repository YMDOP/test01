package com.love.interviewforge.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    Optional<Document> findBySha256(String sha256);
}
