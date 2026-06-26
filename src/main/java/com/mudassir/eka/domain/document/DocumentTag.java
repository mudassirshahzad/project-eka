package com.mudassir.eka.domain.document;

import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;

import java.time.Instant;
import java.util.Objects;

/**
 * A structured label applied to a {@link Document}.
 *
 * Complements the flat {@code tags text[]} column on documents with a normalised
 * taxonomy. The optional {@code category} field namespaces tags into groups such
 * as "department", "classification", or "project", enabling faceted browsing
 * without a full taxonomy management system.
 *
 * Tag values are case-insensitive at the database level (unique index on
 * {@code lower(tag)}). Callers are responsible for normalising case before
 * creating a tag.
 */
public record DocumentTag(
        DocumentTagId id,
        DocumentId    documentId,
        TenantId      tenantId,
        String        tag,
        String        category,
        UserId        createdBy,
        Instant       createdAt
) {

    public DocumentTag {
        Objects.requireNonNull(id,         "id");
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(tenantId,   "tenantId");
        if (tag == null || tag.isBlank()) throw new IllegalArgumentException("tag must not be blank");
        if (tag.length() > 100)           throw new IllegalArgumentException("tag must not exceed 100 characters");
        if (category != null && category.length() > 50)
            throw new IllegalArgumentException("category must not exceed 50 characters");
        Objects.requireNonNull(createdBy,  "createdBy");
        Objects.requireNonNull(createdAt,  "createdAt");
    }

    public static DocumentTag create(
            DocumentId documentId,
            TenantId   tenantId,
            String     tag,
            String     category,
            UserId     createdBy
    ) {
        return new DocumentTag(
                DocumentTagId.generate(),
                documentId,
                tenantId,
                tag.strip().toLowerCase(),
                category,
                createdBy,
                Instant.now()
        );
    }
}
