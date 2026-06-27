package com.mudassir.eka.domain.retrieval.port;

import com.mudassir.eka.domain.shared.TenantId;

public interface QueryRewritePort {

    String rewrite(String queryText, TenantId tenantId);
}
