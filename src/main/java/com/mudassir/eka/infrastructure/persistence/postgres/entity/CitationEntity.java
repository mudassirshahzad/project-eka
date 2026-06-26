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

import java.util.UUID;

@Entity
@Table(name = "citations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CitationEntity extends BaseUuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false, updatable = false)
    private MessageEntity message;

    @Column(name = "chunk_id", nullable = false, updatable = false)
    private UUID chunkId;

    @Column(name = "relevance_score", nullable = false)
    private double relevanceScore;
}
