package com.mudassirshahzad.eka.infrastructure.persistence.postgres.mapper;

import com.mudassirshahzad.eka.domain.document.Document;
import com.mudassirshahzad.eka.domain.document.DocumentMetadata;
import com.mudassirshahzad.eka.domain.document.DocumentStatus;
import com.mudassirshahzad.eka.domain.document.SupportedFormat;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.entity.AuditableEntity;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.entity.BaseUuidEntity;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.entity.DocumentEntity;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.entity.TenantEntity;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.entity.UserEntity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Baseline persistence-mapper coverage (P04.13.4). Pure unit tests — no Spring context, no
 * database. Focused on the {@code Set&lt;String&gt; tags <-> String[]} conversion, the one
 * lossy-looking translation in this mapper, plus the standard scalar/enum round trip.
 *
 * <p>{@code createdAt}/{@code updatedAt} are normally populated by JPA's {@code @PrePersist}
 * lifecycle hook, which never fires when an entity is hand-built outside a real persistence
 * context — {@link #stampTimestamps(BaseUuidEntity)} simulates that hook via reflection so the
 * domain aggregates' non-null invariants are satisfiable in a pure unit test.
 */
class DocumentPersistenceMapperTest {

    private final DocumentPersistenceMapper mapper = new DocumentPersistenceMapper();

    @Test
    void toDomain_mapsScalarAndEnumFields() {
        TenantEntity tenant = tenantEntity();
        UserEntity   owner  = ownerEntity(tenant);
        DocumentEntity entity = documentEntityBuilder(tenant, owner)
                .status(DocumentStatus.INDEXED.name())
                .chunkCount(12)
                .tags(new String[0])
                .build();
        entity.setId(UUID.randomUUID());
        stampTimestamps(entity);

        Document domain = mapper.toDomain(entity);

        assertThat(domain.getId().value()).isEqualTo(entity.getId());
        assertThat(domain.getTenantId().value()).isEqualTo(tenant.getId());
        assertThat(domain.getOwnerId().value()).isEqualTo(owner.getId());
        assertThat(domain.getFilename()).isEqualTo("report.pdf");
        assertThat(domain.getFormat()).isEqualTo(SupportedFormat.PDF);
        assertThat(domain.getStatus()).isEqualTo(DocumentStatus.INDEXED);
        assertThat(domain.getChunkCount()).isEqualTo(12);
    }

    @Test
    void toDomain_nullTags_mapsToEmptySet() {
        DocumentEntity entity = baseEntity().tags(null).build();
        entity.setId(UUID.randomUUID());
        stampTimestamps(entity);

        Document domain = mapper.toDomain(entity);

        assertThat(domain.getMetadata().tags()).isEmpty();
    }

    @Test
    void toDomain_populatedTags_mapsToSet() {
        DocumentEntity entity = baseEntity().tags(new String[]{"finance", "q3"}).build();
        entity.setId(UUID.randomUUID());
        stampTimestamps(entity);

        Document domain = mapper.toDomain(entity);

        assertThat(domain.getMetadata().tags()).containsExactlyInAnyOrder("finance", "q3");
    }

    @Test
    void toEntity_emptyTags_mapsToEmptyArrayNotNull() {
        TenantEntity tenant = tenantEntity();
        UserEntity   owner  = ownerEntity(tenant);
        Document domain = Document.create(
                TenantId.of(tenant.getId()), UserId.of(owner.getId()),
                "empty-tags.txt", SupportedFormat.TXT,
                DocumentMetadata.builder().tags(Set.of()).build());

        DocumentEntity entity = mapper.toEntity(domain, tenant, owner);

        assertThat(entity.getTags()).isNotNull();
        assertThat(entity.getTags()).isEmpty();
    }

    @Test
    void toEntity_thenToDomain_tagsRoundTrip() {
        TenantEntity tenant = tenantEntity();
        UserEntity   owner  = ownerEntity(tenant);
        Document original = Document.create(
                TenantId.of(tenant.getId()), UserId.of(owner.getId()),
                "tagged.pdf", SupportedFormat.PDF,
                DocumentMetadata.builder().tags(Set.of("legal", "contract")).title("Contract").build());

        DocumentEntity entity = mapper.toEntity(original, tenant, owner);
        stampTimestamps(entity);
        Document restored = mapper.toDomain(entity);

        assertThat(restored.getMetadata().tags()).containsExactlyInAnyOrder("legal", "contract");
        assertThat(restored.getMetadata().title()).isEqualTo("Contract");
        assertThat(restored.getFilename()).isEqualTo("tagged.pdf");
        assertThat(restored.getFormat()).isEqualTo(SupportedFormat.PDF);
    }

    @Test
    void updateEntity_appliesStatusAndMetadataChanges() {
        TenantEntity tenant = tenantEntity();
        UserEntity   owner  = ownerEntity(tenant);
        Document domain = Document.create(
                TenantId.of(tenant.getId()), UserId.of(owner.getId()),
                "doc.pdf", SupportedFormat.PDF, DocumentMetadata.EMPTY);
        DocumentEntity entity = mapper.toEntity(domain, tenant, owner);
        entity.setStatus(DocumentStatus.PENDING.name());

        Document reconstituted = Document.reconstitute(
                domain.getId(), domain.getTenantId(), domain.getOwnerId(), domain.getFilename(),
                domain.getFormat(), DocumentStatus.INDEXED,
                DocumentMetadata.builder().title("Updated").build(),
                "raw/path", "parsed/path", 7, null,
                domain.getCreatedAt(), domain.getUpdatedAt(), null);

        mapper.updateEntity(entity, reconstituted);

        assertThat(entity.getStatus()).isEqualTo("INDEXED");
        assertThat(entity.getTitle()).isEqualTo("Updated");
        assertThat(entity.getChunkCount()).isEqualTo(7);
        assertThat(entity.getRawContentPath()).isEqualTo("raw/path");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private DocumentEntity.DocumentEntityBuilder baseEntity() {
        TenantEntity tenant = tenantEntity();
        UserEntity   owner  = ownerEntity(tenant);
        return documentEntityBuilder(tenant, owner);
    }

    private static DocumentEntity.DocumentEntityBuilder documentEntityBuilder(TenantEntity tenant, UserEntity owner) {
        return DocumentEntity.builder()
                .tenant(tenant)
                .owner(owner)
                .filename("report.pdf")
                .format(SupportedFormat.PDF.name())
                .status(DocumentStatus.PENDING.name())
                .chunkCount(0);
    }

    private static TenantEntity tenantEntity() {
        TenantEntity tenant = TenantEntity.builder()
                .name("Acme").slug("acme-" + UUID.randomUUID()).active(true).build();
        tenant.setId(UUID.randomUUID());
        stampTimestamps(tenant);
        return tenant;
    }

    private static UserEntity ownerEntity(TenantEntity tenant) {
        UserEntity owner = UserEntity.builder()
                .tenant(tenant).email("owner@example.com").passwordHash("hash").active(true).build();
        owner.setId(UUID.randomUUID());
        stampTimestamps(owner);
        return owner;
    }

    private static void stampTimestamps(BaseUuidEntity entity) {
        setField(entity, BaseUuidEntity.class, "createdAt", Instant.now());
        if (entity instanceof AuditableEntity auditable) {
            auditable.setUpdatedAt(Instant.now());
        }
    }

    private static void setField(Object target, Class<?> declaringClass, String fieldName, Object value) {
        try {
            Field field = declaringClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
