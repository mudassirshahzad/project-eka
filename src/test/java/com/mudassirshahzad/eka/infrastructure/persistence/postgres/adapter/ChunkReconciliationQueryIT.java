package com.mudassirshahzad.eka.infrastructure.persistence.postgres.adapter;

import com.mudassirshahzad.eka.domain.chunk.Chunk;
import com.mudassirshahzad.eka.domain.chunk.ChunkMetadata;
import com.mudassirshahzad.eka.domain.document.DocumentId;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.entity.DocumentEntity;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.entity.TenantEntity;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.entity.UserEntity;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.mapper.ChunkPersistenceMapper;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.repository.DocumentJpaRepository;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.repository.TenantJpaRepository;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.repository.UserJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the drift-detection query behind the reconciliation job against a real Postgres
 * (WP-3, ADR OR02) — the simulated partial write Phase 7's success criterion asks for.
 *
 * <p>The simulation is faithful rather than contrived: a chunk row is committed with a null
 * {@code vector_id}, which is exactly what the ingestion pipeline leaves behind when Postgres
 * commits and the subsequent Weaviate call fails — a state that became reachable when ADR HD01
 * replaced one long transaction with short per-step ones.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ChunkRepositoryAdapter.class, ChunkPersistenceMapper.class})
class ChunkReconciliationQueryIT {

    @Autowired private ChunkRepositoryAdapter chunkRepository;
    @Autowired private DocumentJpaRepository  documentJpaRepository;
    @Autowired private TenantJpaRepository    tenantJpaRepository;
    @Autowired private UserJpaRepository      userJpaRepository;
    @Autowired private TestEntityManager      entityManager;

    @Test
    void findUnindexed_returnsOnlyChunksWhoseVectorNeverReachedWeaviate() {
        TenantEntity   tenant   = persistTenant();
        DocumentEntity document = persistDocument(tenant);

        Chunk indexed   = newChunk(document, tenant, 0);
        Chunk unindexed = newChunk(document, tenant, 1);
        chunkRepository.saveAll(List.of(indexed, unindexed));

        // Simulate the successful half of ingestion for one chunk only: assignVectorId is called
        // solely after VectorStore.index succeeds, so the other chunk is genuine partial-write drift.
        indexed.assignVectorId(UUID.randomUUID().toString());
        chunkRepository.saveAll(List.of(indexed));
        entityManager.flush();
        entityManager.clear();

        List<UUID> drifted = chunkRepository.findUnindexed(500).stream()
                .map(c -> c.getId().value())
                .toList();

        // Scoped to this test's own rows: findUnindexed is deliberately global (reconciliation
        // must find drift across every tenant), and the shared test database carries chunks
        // committed by other test classes.
        assertThat(drifted).contains(unindexed.getId().value())
                           .doesNotContain(indexed.getId().value());
    }

    @Test
    void findUnindexed_isBoundedByTheRequestedLimit() {
        TenantEntity   tenant   = persistTenant();
        DocumentEntity document = persistDocument(tenant);

        chunkRepository.saveAll(List.of(
                newChunk(document, tenant, 0),
                newChunk(document, tenant, 1),
                newChunk(document, tenant, 2)));
        entityManager.flush();
        entityManager.clear();

        assertThat(chunkRepository.findUnindexed(2)).hasSize(2);
        assertThat(chunkRepository.findUnindexed(0)).isEmpty();
    }

    @Test
    void findUnindexed_returnsNothingWhenEveryChunkIsIndexed() {
        TenantEntity   tenant   = persistTenant();
        DocumentEntity document = persistDocument(tenant);

        Chunk chunk = newChunk(document, tenant, 0);
        chunkRepository.saveAll(List.of(chunk));
        chunk.assignVectorId(UUID.randomUUID().toString());
        chunkRepository.saveAll(List.of(chunk));
        entityManager.flush();
        entityManager.clear();

        assertThat(chunkRepository.findUnindexed(500))
                .extracting(c -> c.getId().value())
                .doesNotContain(chunk.getId().value());
    }

    private Chunk newChunk(DocumentEntity document, TenantEntity tenant, int sequence) {
        return Chunk.create(
                DocumentId.of(document.getId()), TenantId.of(tenant.getId()),
                sequence, "chunk content " + sequence, ChunkMetadata.of("sliding-window"));
    }

    private TenantEntity persistTenant() {
        TenantEntity tenant = new TenantEntity();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Reconciliation Tenant");
        tenant.setSlug("reconciliation-" + UUID.randomUUID());
        tenant.setActive(true);
        return tenantJpaRepository.saveAndFlush(tenant);
    }

    private DocumentEntity persistDocument(TenantEntity tenant) {
        UserEntity uploader = UserEntity.builder()
                .tenant(tenant)
                .email("uploader-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hash")
                .active(true)
                .build();
        uploader.setId(UUID.randomUUID());
        userJpaRepository.saveAndFlush(uploader);

        DocumentEntity document = new DocumentEntity();
        document.setId(UUID.randomUUID());
        document.setTenant(tenant);
        document.setOwner(uploader);
        document.setFilename("reconciliation.txt");
        document.setFormat("TXT");
        document.setStatus("INDEXED");
        document.setRawContentPath("/tmp/reconciliation.txt");
        document.setClassification("INTERNAL");
        return documentJpaRepository.saveAndFlush(document);
    }

    @Test
    void reSavingAnAlreadyPersistedChunk_recordsTheVectorIdAndPreservesCreatedAt() {
        // Regression test for the bug this work package's first real-database run exposed
        // (WP-3, ADR OR04). DocumentIndexingService saves chunks a second time to record the
        // vectorId that indexing produced, but the adapter mapped every chunk to a *fresh*
        // detached entity with a null createdAt; because created_at is updatable=false, the
        // round trip came back null and Chunk.reconstitute rejected it. This is the ingestion
        // path's normal second save, not an edge case — it simply had no real-database coverage.
        TenantEntity   tenant   = persistTenant();
        DocumentEntity document = persistDocument(tenant);

        Chunk chunk = newChunk(document, tenant, 0);
        Chunk firstSave = chunkRepository.saveAll(List.of(chunk)).getFirst();
        assertThat(firstSave.getCreatedAt()).isNotNull();
        entityManager.flush();
        entityManager.clear();

        chunk.assignVectorId("vector-abc");
        Chunk secondSave = chunkRepository.saveAll(List.of(chunk)).getFirst();

        assertThat(secondSave.getVectorId()).isEqualTo("vector-abc");
        assertThat(secondSave.getCreatedAt())
                .as("createdAt must survive the indexing re-save")
                .isNotNull()
                .isEqualTo(firstSave.getCreatedAt());
        assertThat(chunkRepository.findUnindexed(500))
                .extracting(c -> c.getId().value())
                .doesNotContain(chunk.getId().value());
    }
}
