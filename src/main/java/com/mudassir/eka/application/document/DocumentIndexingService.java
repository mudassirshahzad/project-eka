package com.mudassir.eka.application.document;

import com.mudassir.eka.application.event.ChunkIndexedEvent;
import com.mudassir.eka.application.shared.DomainEventPublisher;
import com.mudassir.eka.domain.chunk.Chunk;
import com.mudassir.eka.domain.chunk.ChunkRepository;
import com.mudassir.eka.domain.chunk.VectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIndexingService {

    private final VectorStore          vectorStore;
    private final ChunkRepository      chunkRepository;
    private final DomainEventPublisher eventPublisher;

    public List<Chunk> index(List<Chunk> chunks) {
        if (chunks.isEmpty()) return List.of();

        // Idempotency: if any chunks carry a stale vectorId from a previous indexing
        // run, delete those vectors first so the UNIQUE constraint isn't violated.
        List<String> staleVectorIds = chunks.stream()
                .filter(c -> c.getVectorId() != null)
                .map(Chunk::getVectorId)
                .toList();
        if (!staleVectorIds.isEmpty()) {
            vectorStore.deleteByIds(staleVectorIds);
            chunks.forEach(Chunk::clearVectorId);
            log.debug("Cleared {} stale vector(s) before re-indexing", staleVectorIds.size());
        }

        // Index into Weaviate — adapter assigns a new vectorId to each chunk via mutation
        vectorStore.index(chunks);

        // Persist the updated vectorIds in PostgreSQL
        List<Chunk> saved = chunkRepository.saveAll(chunks);

        for (Chunk chunk : saved) {
            eventPublisher.publish(new ChunkIndexedEvent(
                    chunk.getId(), chunk.getDocumentId(), chunk.getTenantId(), chunk.getVectorId()));
        }

        log.info("Indexed {} chunk(s) into vector store", saved.size());
        return saved;
    }
}
