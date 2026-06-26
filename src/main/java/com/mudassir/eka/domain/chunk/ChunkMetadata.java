package com.mudassir.eka.domain.chunk;

public record ChunkMetadata(
        Integer pageNumber,
        String  sectionTitle,
        Integer startOffset,
        Integer endOffset,
        Integer tokenCount,
        String  chunkingStrategy
) {

    public ChunkMetadata {
        if (chunkingStrategy == null || chunkingStrategy.isBlank()) {
            throw new IllegalArgumentException("chunkingStrategy must not be blank");
        }
    }

    public static ChunkMetadata of(String chunkingStrategy) {
        return new ChunkMetadata(null, null, null, null, null, chunkingStrategy);
    }

    public static Builder builder(String chunkingStrategy) {
        return new Builder(chunkingStrategy);
    }

    public static class Builder {
        private final String chunkingStrategy;
        private Integer pageNumber;
        private String  sectionTitle;
        private Integer startOffset;
        private Integer endOffset;
        private Integer tokenCount;

        private Builder(String chunkingStrategy) { this.chunkingStrategy = chunkingStrategy; }

        public Builder pageNumber(Integer pageNumber)       { this.pageNumber    = pageNumber;    return this; }
        public Builder sectionTitle(String sectionTitle)   { this.sectionTitle  = sectionTitle;  return this; }
        public Builder startOffset(Integer startOffset)    { this.startOffset   = startOffset;   return this; }
        public Builder endOffset(Integer endOffset)        { this.endOffset     = endOffset;     return this; }
        public Builder tokenCount(Integer tokenCount)      { this.tokenCount    = tokenCount;    return this; }

        public ChunkMetadata build() {
            return new ChunkMetadata(pageNumber, sectionTitle, startOffset, endOffset, tokenCount, chunkingStrategy);
        }
    }
}
