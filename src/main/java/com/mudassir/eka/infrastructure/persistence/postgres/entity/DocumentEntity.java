package com.mudassir.eka.infrastructure.persistence.postgres.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "documents")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentEntity extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private TenantEntity tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private UserEntity owner;

    @Column(name = "filename", nullable = false, length = 500)
    private String filename;

    @Column(name = "format", nullable = false, length = 20)
    private String format;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "author", length = 255)
    private String author;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "department", length = 255)
    private String department;

    @Column(name = "classification", length = 50)
    private String classification;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", columnDefinition = "text[]")
    private String[] tags;

    @Column(name = "raw_content_path", length = 1000)
    private String rawContentPath;

    @Column(name = "parsed_text_path", length = 1000)
    private String parsedTextPath;

    @Column(name = "chunk_count", nullable = false)
    @Builder.Default
    private int chunkCount = 0;

    @Column(name = "ingestion_error", columnDefinition = "TEXT")
    private String ingestionError;

    @Column(name = "deleted_at", columnDefinition = "TIMESTAMPTZ")
    private Instant deletedAt;
}
