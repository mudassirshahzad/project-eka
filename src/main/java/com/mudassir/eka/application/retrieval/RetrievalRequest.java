package com.mudassir.eka.application.retrieval;

import com.mudassir.eka.domain.query.MetadataFilter;
import com.mudassir.eka.domain.retrieval.model.RetrievalOptions;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;

public record RetrievalRequest(
        String queryText,
        TenantId tenantId,
        UserId userId,
        MetadataFilter filter,
        RetrievalOptions options) {
}
