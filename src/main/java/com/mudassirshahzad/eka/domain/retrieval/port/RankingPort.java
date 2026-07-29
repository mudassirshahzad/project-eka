package com.mudassirshahzad.eka.domain.retrieval.port;

import com.mudassirshahzad.eka.domain.retrieval.model.RetrievedChunk;

import java.util.List;

public interface RankingPort {

    List<RetrievedChunk> rank(List<RetrievedChunk> candidates, String queryText);
}
