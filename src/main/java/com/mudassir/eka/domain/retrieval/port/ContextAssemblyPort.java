package com.mudassir.eka.domain.retrieval.port;

import com.mudassir.eka.domain.retrieval.model.RetrievedChunk;

import java.util.List;

public interface ContextAssemblyPort {

    String assemble(List<RetrievedChunk> chunks, String queryText, int tokenBudget);
}
