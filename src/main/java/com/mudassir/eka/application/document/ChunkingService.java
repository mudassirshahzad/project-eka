package com.mudassir.eka.application.document;

import com.mudassir.eka.domain.chunk.Chunk;
import com.mudassir.eka.domain.chunk.ChunkMetadata;
import com.mudassir.eka.domain.document.DocumentId;
import com.mudassir.eka.domain.document.ParsedDocument;
import com.mudassir.eka.domain.shared.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChunkingService {

    private final ChunkingStrategy chunkingStrategy;

    public List<Chunk> chunk(ParsedDocument parsedDocument, DocumentId documentId, TenantId tenantId) {
        List<TextSegment> segments = chunkingStrategy.chunk(parsedDocument.extractedText());
        List<Chunk> chunks = new ArrayList<>(segments.size());
        for (TextSegment segment : segments) {
            ChunkMetadata metadata = ChunkMetadata.builder(chunkingStrategy.name())
                    .startOffset(segment.startOffset())
                    .endOffset(segment.endOffset())
                    .tokenCount(segment.tokenCount())
                    .build();
            chunks.add(Chunk.create(documentId, tenantId, segment.sequenceNumber(), segment.content(), metadata));
        }
        return List.copyOf(chunks);
    }
}
