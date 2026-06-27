package com.mudassir.eka.infrastructure.embedding;

import com.mudassir.eka.domain.chunk.EmbeddingProvider;
import com.mudassir.eka.infrastructure.embedding.exception.EmbeddingException;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class OllamaEmbeddingProvider implements EmbeddingProvider {

    private final EmbeddingModel embeddingModel;
    private final String         modelName;

    public OllamaEmbeddingProvider(
            EmbeddingModel embeddingModel,
            @Value("${spring.ai.ollama.embedding.model:nomic-embed-text}") String modelName
    ) {
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "embeddingModel");
        this.modelName      = Objects.requireNonNull(modelName,      "modelName");
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        Objects.requireNonNull(texts, "texts");
        if (texts.isEmpty()) return List.of();
        try {
            return embeddingModel.call(new EmbeddingRequest(texts, null))
                    .getResults()
                    .stream()
                    .map(e -> e.getOutput())
                    .toList();
        } catch (Exception ex) {
            throw new EmbeddingException("Embedding failed for " + texts.size() + " texts", ex);
        }
    }

    @Override
    public String modelName() { return modelName; }

    @Override
    public int dimension() { return embeddingModel.dimensions(); }
}
