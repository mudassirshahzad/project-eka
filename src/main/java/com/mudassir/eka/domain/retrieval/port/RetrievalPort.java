package com.mudassir.eka.domain.retrieval.port;

import com.mudassir.eka.domain.query.MetadataFilter;
import com.mudassir.eka.domain.retrieval.model.RetrievalOptions;
import com.mudassir.eka.domain.retrieval.model.RetrievalResult;
import com.mudassir.eka.domain.shared.TenantId;

public interface RetrievalPort {

    RetrievalResult retrieve(
            String queryText,
            TenantId tenantId,
            MetadataFilter filter,
            RetrievalOptions options);
}
