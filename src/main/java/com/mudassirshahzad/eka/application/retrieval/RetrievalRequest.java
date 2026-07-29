package com.mudassirshahzad.eka.application.retrieval;

import com.mudassirshahzad.eka.domain.query.MetadataFilter;
import com.mudassirshahzad.eka.domain.retrieval.model.RetrievalOptions;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;

public record RetrievalRequest(
        String queryText,
        TenantId tenantId,
        UserId userId,
        MetadataFilter filter,
        RetrievalOptions options) {
}
