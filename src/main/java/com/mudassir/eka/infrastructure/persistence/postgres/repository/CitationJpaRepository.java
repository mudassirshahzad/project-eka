package com.mudassir.eka.infrastructure.persistence.postgres.repository;

import com.mudassir.eka.infrastructure.persistence.postgres.entity.CitationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CitationJpaRepository extends JpaRepository<CitationEntity, UUID> {

    List<CitationEntity> findByMessage_Id(UUID messageId);

    List<CitationEntity> findByChunkId(UUID chunkId);
}
