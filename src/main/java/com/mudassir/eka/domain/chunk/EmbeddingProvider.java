package com.mudassir.eka.domain.chunk;

import java.util.List;

public interface EmbeddingProvider {

    List<float[]> embed(List<String> texts);

    String modelName();

    int dimension();
}
