package com.mudassir.eka.application.document;

import com.mudassir.eka.application.event.ChunkCreatedEvent;
import com.mudassir.eka.application.event.ChunkEmbeddedEvent;
import com.mudassir.eka.application.shared.DomainEventPublisher;
import com.mudassir.eka.domain.chunk.Chunk;
import com.mudassir.eka.domain.chunk.ChunkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ChunkApplicationService {

    private final ChunkRepository      chunkRepository;
    private final DomainEventPublisher eventPublisher;

    /**
     * Persists all chunks and returns them paired with their pre-computed embedding vectors.
     *
     * <p>The returned list is positionally aligned with the input: index {@code i} of the result
     * carries the saved {@link Chunk} together with the {@code float[]} vector originally computed
     * for that chunk. Callers must pass the returned list directly to the vector-store indexing
     * step to avoid re-embedding.
     */
    public List<EmbeddedChunk> saveAll(List<EmbeddedChunk> embeddedChunks) {
        List<Chunk> chunks = embeddedChunks.stream().map(EmbeddedChunk::chunk).toList();
        List<Chunk> saved  = chunkRepository.saveAll(chunks);

        List<EmbeddedChunk> savedEmbedded = new ArrayList<>(saved.size());
        for (int i = 0; i < saved.size(); i++) {
            Chunk chunk = saved.get(i);
            savedEmbedded.add(new EmbeddedChunk(chunk, embeddedChunks.get(i).embedding()));
            eventPublisher.publish(new ChunkCreatedEvent(
                    chunk.getId(), chunk.getDocumentId(), chunk.getTenantId(), chunk.getSequenceNumber()));
            eventPublisher.publish(new ChunkEmbeddedEvent(
                    chunk.getId(), chunk.getDocumentId(), chunk.getTenantId(),
                    chunk.getEmbeddingModel(), chunk.getEmbeddingDimension()));
        }
        return List.copyOf(savedEmbedded);
    }
}
