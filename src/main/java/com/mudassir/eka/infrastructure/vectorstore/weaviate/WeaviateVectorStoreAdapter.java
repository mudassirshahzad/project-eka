package com.mudassir.eka.infrastructure.vectorstore.weaviate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.base.Result;
import io.weaviate.client.v1.batch.model.ObjectGetResponse;
import io.weaviate.client.v1.data.model.WeaviateObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
public class WeaviateVectorStoreAdapter implements VectorStore {

    // Spring AI 1.0.0 storage format constants (confirmed from WeaviateVectorStore bytecode):
    // - document text is stored under the "content" property key
    // - full metadata map is stored as a JSON string under "metadata"
    // - each searchable metadata field is also stored as "meta_<fieldName>"
    private static final String CONTENT_FIELD_NAME  = "content";
    private static final String METADATA_FIELD_NAME = "metadata";
    private static final String METADATA_FIELD_PREFIX = "meta_";

    private final org.springframework.ai.vectorstore.VectorStore springVectorStore;
    private final WeaviateClient          weaviateClient;
    private final MetadataFilterTranslator filterTranslator;
    private final AppProperties            appProperties;
    private final ObjectMapper             objectMapper;
    private final String                   weaviateObjectClass;
    private final String                   consistencyLevel;

    public WeaviateVectorStoreAdapter(
            org.springframework.ai.vectorstore.VectorStore springVectorStore,
            WeaviateClient weaviateClient,
            MetadataFilterTranslator filterTranslator,
            AppProperties appProperties,
            ObjectMapper objectMapper,
            @Value("${spring.ai.vectorstore.weaviate.object-class:DocumentChunk}") String weaviateObjectClass,
            @Value("${spring.ai.vectorstore.weaviate.consistency-level:ONE}") String consistencyLevel) {
        this.springVectorStore   = springVectorStore;
        this.weaviateClient      = weaviateClient;
        this.filterTranslator    = filterTranslator;
        this.appProperties       = appProperties;
        this.objectMapper        = objectMapper;
        this.weaviateObjectClass = weaviateObjectClass;
        this.consistencyLevel    = consistencyLevel;
    }

    /**
     * Indexes chunks into Weaviate using the provided pre-computed embedding vectors.
     *
     * <p>This method bypasses Spring AI's internal embedding call and writes directly to the
     * Weaviate batch API, ensuring each chunk is embedded exactly once (by the caller, via
     * {@link com.mudassir.eka.domain.chunk.EmbeddingProvider}).  The Weaviate object structure
     * replicates Spring AI 1.0.0's storage format so that
     * {@link #search(String, int, MetadataFilter)} — which still delegates to the Spring AI
     * {@code VectorStore} — can deserialise results correctly.
     */
    @Override
    public void index(List<Chunk> chunks, List<float[]> precomputedVectors) {
        if (chunks.isEmpty()) return;
        if (chunks.size() != precomputedVectors.size()) {
            throw new VectorIndexingException(
                    "Chunk count " + chunks.size() + " != vector count " + precomputedVectors.size(), null);
        }

        // Build Weaviate objects with pre-computed vectors; track vectorId → Chunk for
        // the post-success assignVectorId mutation.
        Map<String, Chunk> vectorIdToChunk = new LinkedHashMap<>(chunks.size());
        List<WeaviateObject> weaviateObjects = new ArrayList<>(chunks.size());

        for (int i = 0; i < chunks.size(); i++) {
            Chunk   chunk    = chunks.get(i);
            float[] vector   = precomputedVectors.get(i);
            String  vectorId = UUID.randomUUID().toString();

            weaviateObjects.add(WeaviateObject.builder()
                    .className(weaviateObjectClass)
                    .id(vectorId)
                    .properties(toWeaviateProperties(chunk))
                    .vector(box(vector))
                    .build());

            vectorIdToChunk.put(vectorId, chunk);
        }

        try {
            int batchSize = appProperties.ingestion().embeddingBatchSize();
            List<List<WeaviateObject>> batches = partition(weaviateObjects, batchSize);
            for (List<WeaviateObject> batch : batches) {
                Result<ObjectGetResponse[]> result = weaviateClient.batch()
                        .objectsBatcher()
                        .withObjects(batch.toArray(new WeaviateObject[0]))
                        .withConsistencyLevel(consistencyLevel)
                        .run();
                checkErrors(result);
            }
            log.debug("Indexed {} chunk(s) across {} batch(es) using pre-computed vectors",
                    chunks.size(), batches.size());
        } catch (VectorIndexingException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new VectorIndexingException(
                    "Failed to index " + chunks.size() + " chunk(s) into vector store", ex);
        }

        // Assign vectorIds only after all batches succeed (mirrors previous behaviour)
        vectorIdToChunk.forEach((vectorId, chunk) -> chunk.assignVectorId(vectorId));
    }

    @Override
    public List<VectorSearchResult> search(String queryText, int topK, MetadataFilter filter) {
        try {
            Filter.Expression filterExpression = filterTranslator.translate(filter);

            SearchRequest.Builder builder = SearchRequest.builder()
                    .query(queryText)
                    .topK(topK);
            if (filterExpression != null) {
                builder.filterExpression(filterExpression);
            }
            SearchRequest request = builder.build();

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

    /**
     * Builds the Weaviate properties map in the format expected by Spring AI 1.0.0's
     * {@code WeaviateVectorStore} deserialization logic:
     * <ul>
     *   <li>{@code "content"} — plain-text chunk content</li>
     *   <li>{@code "metadata"} — JSON-serialized metadata map (full metadata roundtrip)</li>
     *   <li>{@code "meta_<key>"} — each metadata entry repeated with the prefix used by
     *       Spring AI's filter expression converter (enables tenant/department filtering
     *       during vector search without touching the {@code springVectorStore} search path)</li>
     * </ul>
     */
    private Map<String, Object> toWeaviateProperties(Chunk chunk) {
        Map<String, Object> metadataMap = toMetadata(chunk);

        Map<String, Object> properties = new HashMap<>();
        properties.put(CONTENT_FIELD_NAME, chunk.getContent());

        try {
            properties.put(METADATA_FIELD_NAME, objectMapper.writeValueAsString(metadataMap));
        } catch (JsonProcessingException ex) {
            throw new VectorIndexingException(
                    "Failed to serialise metadata for chunk " + chunk.getId().value(), ex);
        }

        // Repeat each metadata value with the "meta_" prefix so Spring AI's Weaviate
        // filter expressions (used during similarity search) resolve correctly.
        metadataMap.forEach((k, v) -> properties.put(METADATA_FIELD_PREFIX + k, v));

        return properties;
    }

    private Map<String, Object> toMetadata(Chunk chunk) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("chunkId",         chunk.getId().value().toString());
        metadata.put("documentId",      chunk.getDocumentId().value().toString());
        metadata.put("tenantId",        chunk.getTenantId().value().toString());
        metadata.put("sequenceNumber",  chunk.getSequenceNumber());

        ChunkMetadata cm = chunk.getMetadata();
        metadata.put("chunkingStrategy", cm.chunkingStrategy());
        if (cm.pageNumber()   != null) metadata.put("pageNumber",   cm.pageNumber());
        if (cm.sectionTitle() != null) metadata.put("sectionTitle", cm.sectionTitle());
        if (cm.tokenCount()   != null) metadata.put("tokenCount",   cm.tokenCount());

        return metadata;
    }

    private VectorSearchResult toSearchResult(Document doc) {
        String chunkIdStr = (String) doc.getMetadata().get("chunkId");
        ChunkId chunkId   = chunkIdStr != null ? ChunkId.of(chunkIdStr) : null;

        double score = doc.getScore() != null ? doc.getScore() : 0.0;

        return new VectorSearchResult(chunkId, doc.getId(), doc.getText(), score);
    }

    private static Float[] box(float[] primitive) {
        Float[] boxed = new Float[primitive.length];
        for (int i = 0; i < primitive.length; i++) {
            boxed[i] = primitive[i];
        }
        return boxed;
    }

    private static void checkErrors(Result<ObjectGetResponse[]> result) {
        if (result.hasErrors()) {
            String errors = result.getError().getMessages().stream()
                    .map(m -> m.getMessage())
                    .collect(Collectors.joining("\n"));
            throw new VectorIndexingException("Weaviate batch insert failed:\n" + errors, null);
        }
    }

    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}
