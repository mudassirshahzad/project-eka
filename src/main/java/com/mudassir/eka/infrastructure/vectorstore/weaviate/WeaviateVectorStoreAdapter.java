package com.mudassir.eka.infrastructure.vectorstore.weaviate;

import com.mudassir.eka.domain.chunk.Chunk;
import com.mudassir.eka.domain.chunk.ChunkId;
import com.mudassir.eka.domain.chunk.ChunkMetadata;
import com.mudassir.eka.domain.chunk.VectorSearchResult;
import com.mudassir.eka.domain.chunk.VectorStore;
import com.mudassir.eka.domain.query.MetadataFilter;
import com.mudassir.eka.infrastructure.config.AppProperties;
import com.mudassir.eka.infrastructure.vectorstore.weaviate.exception.VectorIndexingException;
import com.mudassir.eka.infrastructure.vectorstore.weaviate.exception.VectorSearchException;
import com.mudassir.eka.infrastructure.vectorstore.weaviate.exception.VectorStoreException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeaviateVectorStoreAdapter implements VectorStore {

    private final org.springframework.ai.vectorstore.VectorStore springVectorStore;
    private final MetadataFilterTranslator filterTranslator;
    private final AppProperties            appProperties;

    @Override
    public void index(List<Chunk> chunks) {
        if (chunks.isEmpty()) return;

        // Map vectorId → Chunk so we can assign after successful indexing
        Map<String, Chunk> vectorIdToChunk = new LinkedHashMap<>(chunks.size());
        List<Document> documents = new ArrayList<>(chunks.size());

        for (Chunk chunk : chunks) {
            String vectorId = UUID.randomUUID().toString();
            documents.add(new Document(vectorId, chunk.getContent(), toMetadata(chunk)));
            vectorIdToChunk.put(vectorId, chunk);
        }

        try {
            List<List<Document>> batches = partition(documents, appProperties.ingestion().embeddingBatchSize());
            for (List<Document> batch : batches) {
                springVectorStore.add(batch);
            }
            log.debug("Indexed {} chunks across {} batch(es)", chunks.size(), batches.size());
        } catch (Exception ex) {
            throw new VectorIndexingException(
                    "Failed to index " + chunks.size() + " chunks into vector store", ex);
        }

        // Assign vectorIds only after all batches succeed
        vectorIdToChunk.forEach((vectorId, chunk) -> chunk.assignVectorId(vectorId));
    }

    @Override
    public List<VectorSearchResult> search(String queryText, int topK, MetadataFilter filter) {
        try {
            Filter.Expression filterExpression = filterTranslator.translate(filter);

            SearchRequest request = SearchRequest.builder()
                    .query(queryText)
                    .topK(topK)
                    .filterExpression(filterExpression)
                    .build();

            return springVectorStore.similaritySearch(request)
                    .stream()
                    .map(this::toSearchResult)
                    .toList();
        } catch (VectorSearchException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new VectorSearchException(
                    "Vector similarity search failed for query length " + queryText.length(), ex);
        }
    }

    @Override
    public void deleteByIds(List<String> vectorIds) {
        if (vectorIds.isEmpty()) return;
        try {
            springVectorStore.delete(vectorIds);
            log.debug("Deleted {} vector(s) from store", vectorIds.size());
        } catch (Exception ex) {
            throw new VectorStoreException(
                    "Failed to delete " + vectorIds.size() + " vector(s) from store", ex);
        }
    }

    private Map<String, Object> toMetadata(Chunk chunk) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("chunkId",         chunk.getId().value().toString());
        metadata.put("documentId",      chunk.getDocumentId().value().toString());
        metadata.put("tenantId",        chunk.getTenantId().value().toString());
        metadata.put("sequenceNumber",  chunk.getSequenceNumber());

        ChunkMetadata cm = chunk.getMetadata();
        metadata.put("chunkingStrategy", cm.chunkingStrategy());
        if (cm.pageNumber()    != null) metadata.put("pageNumber",   cm.pageNumber());
        if (cm.sectionTitle()  != null) metadata.put("sectionTitle", cm.sectionTitle());
        if (cm.tokenCount()    != null) metadata.put("tokenCount",   cm.tokenCount());

        return metadata;
    }

    private VectorSearchResult toSearchResult(Document doc) {
        String chunkIdStr = (String) doc.getMetadata().get("chunkId");
        ChunkId chunkId   = chunkIdStr != null ? ChunkId.of(chunkIdStr) : null;

        double score = doc.getScore() != null ? doc.getScore() : 0.0;

        return new VectorSearchResult(chunkId, doc.getId(), doc.getText(), score);
    }

    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}
