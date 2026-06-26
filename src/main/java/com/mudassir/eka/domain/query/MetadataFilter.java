package com.mudassir.eka.domain.query;

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

        public Builder department(String department)     { criteria.put("department", department);     return this; }
        public Builder classification(String cls)        { criteria.put("classification", cls);         return this; }
        public Builder tags(List<String> tags)           { criteria.put("tags", tags);                  return this; }
        public Builder put(String key, Object value)     { criteria.put(key, value);                    return this; }

        public MetadataFilter build() {
            return new MetadataFilter(criteria);
        }
    }
}
