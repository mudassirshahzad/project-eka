package com.mudassir.eka.domain.chunk;

import com.mudassir.eka.domain.query.MetadataFilter;

import java.util.List;

public interface VectorStore {

    /**
     * Indexes chunks in the vector store using pre-computed embedding vectors.
     *
     * <p>The caller is responsible for generating the embedding vectors before invoking this
     * method. The vector at position {@code i} must correspond to the chunk at position {@code i}.
     * Both lists must have the same size.
     *
     * <p>Each chunk has its {@link Chunk#assignVectorId(String)} called after successful indexing.
     * Implementations must not call an embedding model internally; the provided vectors are the
     * sole source of embeddings.
     *
     * @param chunks             the chunks to index; must not be null or empty
     * @param precomputedVectors the embedding vector for each chunk, positionally aligned
     * @throws IllegalArgumentException if {@code chunks.size() != precomputedVectors.size()}
     */
    void index(List<Chunk> chunks, List<float[]> precomputedVectors);

    List<VectorSearchResult> search(String queryText, int topK, MetadataFilter filter);

    void deleteByIds(List<String> vectorIds);
}
