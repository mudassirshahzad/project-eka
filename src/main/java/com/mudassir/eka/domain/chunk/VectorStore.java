package com.mudassir.eka.domain.chunk;

import com.mudassir.eka.domain.query.MetadataFilter;

import java.util.List;

public interface VectorStore {

    /**
     * Embeds and indexes chunks in the vector store.
     * Each chunk has its {@link Chunk#assignVectorId(String)} called after successful indexing.
     */
    void index(List<Chunk> chunks);

    List<VectorSearchResult> search(String queryText, int topK, MetadataFilter filter);

    void deleteByIds(List<String> vectorIds);
}
