package com.mudassir.eka.infrastructure.persistence.postgres.repository;

import com.mudassir.eka.infrastructure.persistence.postgres.entity.ChunkEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChunkJpaRepository extends JpaRepository<ChunkEntity, UUID> {

    Optional<ChunkEntity> findByVectorId(String vectorId);

    List<ChunkEntity> findByDocument(DocumentEntity document);

    List<ChunkEntity> findByIdIn(List<UUID> ids);

    @Modifying
    @Query("DELETE FROM ChunkEntity c WHERE c.document.id = :documentId")
    void deleteByDocumentId(@Param("documentId") UUID documentId);
}
