package com.mudassirshahzad.eka.domain.retrieval.port;

import com.mudassirshahzad.eka.domain.shared.TenantId;

public interface QueryRewritePort {

    String rewrite(String queryText, TenantId tenantId);
}
