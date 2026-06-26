package com.mudassir.eka.domain.document;

import java.util.Collections;
import java.util.Set;

public record DocumentMetadata(
        String title,
        String author,
        String description,
        String department,
        String classification,
        Set<String> tags
) {

    public static final DocumentMetadata EMPTY = new DocumentMetadata(null, null, null, null, null, Set.of());

    public DocumentMetadata {
        tags = tags != null ? Collections.unmodifiableSet(tags) : Set.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String title;
        private String author;
        private String description;
        private String department;
        private String classification;
        private Set<String> tags = Set.of();

        public Builder title(String title)                   { this.title          = title;          return this; }
        public Builder author(String author)                 { this.author         = author;         return this; }
        public Builder description(String description)       { this.description    = description;    return this; }
        public Builder department(String department)         { this.department     = department;     return this; }
        public Builder classification(String classification) { this.classification = classification; return this; }
        public Builder tags(Set<String> tags)                { this.tags           = tags;           return this; }

        public DocumentMetadata build() {
            return new DocumentMetadata(title, author, description, department, classification, tags);
        }
    }
}
