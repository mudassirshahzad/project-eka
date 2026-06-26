package com.mudassir.eka.infrastructure.persistence.postgres.repository;

import com.mudassir.eka.infrastructure.persistence.postgres.entity.DocumentEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.DocumentTagEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.TenantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DocumentTagJpaRepository extends JpaRepository<DocumentTagEntity, UUID> {

    List<DocumentTagEntity> findByDocument(DocumentEntity document);

    @Query("SELECT t FROM DocumentTagEntity t WHERE t.tenant = :tenant AND lower(t.tag) = lower(:tag)")
    List<DocumentTagEntity> findByTenantAndTagIgnoreCase(
            @Param("tenant") TenantEntity tenant,
            @Param("tag") String tag);

    List<DocumentTagEntity> findByTenantAndCategory(TenantEntity tenant, String category);

    void deleteByDocument(DocumentEntity document);
}
