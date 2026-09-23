package com.mudassirshahzad.eka.application.retrieval;

import com.mudassirshahzad.eka.domain.query.MetadataFilter;
import com.mudassirshahzad.eka.domain.retrieval.model.RetrievalOptions;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;
import com.mudassirshahzad.eka.domain.user.UserRole;

import java.util.Set;

/**
 * @param roles the caller's roles, used by the Authorization Filter (P06.2) to scope results to
 *              the caller's document-classification clearance — see {@code RetrievalService}.
 *              Never {@code null}; an empty set is valid and clears nothing (fail-closed).
 */
public record RetrievalRequest(
        String queryText,
        TenantId tenantId,
        UserId userId,
        Set<UserRole> roles,
        MetadataFilter filter,
        RetrievalOptions options) {
}
