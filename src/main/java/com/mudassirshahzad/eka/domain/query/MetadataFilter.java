package com.mudassirshahzad.eka.domain.query;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record MetadataFilter(Map<String, Object> criteria) {

    public static final MetadataFilter NONE = new MetadataFilter(Map.of());

    public MetadataFilter {
        criteria = criteria != null
                ? Collections.unmodifiableMap(new HashMap<>(criteria))
                : Map.of();
    }

    public boolean isEmpty() {
        return criteria.isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Map<String, Object> criteria = new HashMap<>();

        // No "maximum classification level" criterion here (P06.2): every criteria entry is
        // interpreted uniformly by each RetrievalPort adapter's translator as an exact/membership
        // match against a stored per-item property, and Weaviate does not index document
        // classification per chunk. A numeric "<=" clearance comparison doesn't fit that generic
        // contract safely across engines — see RetrievalService for where that check actually
        // happens (a single post-fetch pass over RetrievedChunk.documentId(), engine-agnostic).
        public Builder department(String department)     { criteria.put("department", department);     return this; }
        public Builder classification(String cls)        { criteria.put("classification", cls);         return this; }
        public Builder tags(List<String> tags)           { criteria.put("tags", tags);                  return this; }
        public Builder put(String key, Object value)     { criteria.put(key, value);                    return this; }

        public MetadataFilter build() {
            return new MetadataFilter(criteria);
        }
    }
}
