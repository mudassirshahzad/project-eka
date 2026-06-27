package com.mudassir.eka.application.document;

import com.mudassir.eka.application.event.ChunkCreatedEvent;
import com.mudassir.eka.application.event.ChunkEmbeddedEvent;
import com.mudassir.eka.application.shared.DomainEventPublisher;
import com.mudassir.eka.domain.chunk.Chunk;
import com.mudassir.eka.domain.chunk.ChunkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ChunkApplicationService {

    private final ChunkRepository      chunkRepository;
    private final DomainEventPublisher eventPublisher;

    public List<Chunk> saveAll(List<EmbeddedChunk> embeddedChunks) {
        List<Chunk> chunks = embeddedChunks.stream().map(EmbeddedChunk::chunk).toList();
        List<Chunk> saved  = chunkRepository.saveAll(chunks);
        for (Chunk chunk : saved) {
            eventPublisher.publish(new ChunkCreatedEvent(
                    chunk.getId(), chunk.getDocumentId(), chunk.getTenantId(), chunk.getSequenceNumber()));
            eventPublisher.publish(new ChunkEmbeddedEvent(
                    chunk.getId(), chunk.getDocumentId(), chunk.getTenantId(),
                    chunk.getEmbeddingModel(), chunk.getEmbeddingDimension()));
        }
        return saved;
    }
}
