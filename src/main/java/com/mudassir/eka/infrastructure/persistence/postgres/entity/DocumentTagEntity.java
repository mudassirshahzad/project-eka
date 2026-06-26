package com.mudassir.eka.infrastructure.persistence.postgres.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(
    name = "document_tags",
    indexes = {
        @Index(name = "idx_document_tags_document",        columnList = "document_id"),
        @Index(name = "idx_document_tags_tenant_category", columnList = "tenant_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentTagEntity extends BaseUuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false, updatable = false)
    private DocumentEntity document;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private TenantEntity tenant;

    @Column(name = "tag", nullable = false, updatable = false, length = 100)
    private String tag;

    @Column(name = "category", length = 50)
    private String category;

    // Stored as raw UUID — no FK to avoid loading UserEntity for a lightweight read
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;
}
