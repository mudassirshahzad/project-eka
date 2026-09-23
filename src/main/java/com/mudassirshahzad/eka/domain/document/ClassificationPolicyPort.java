package com.mudassirshahzad.eka.domain.document;

import com.mudassirshahzad.eka.domain.user.UserRole;

import java.util.Set;

/**
 * The sole place role-based document-classification clearance is decided (Phase 6, P06.2). Both
 * the retrieval pipeline ({@code RetrievalService}) and the REST document endpoints
 * ({@code DocumentController}'s backing use cases) call this port rather than each re-implementing
 * the role → clearance mapping or the clearance-vs-classification comparison — keeping the two
 * access paths from ever drifting into two different authorization rules.
 *
 * <p>A caller may hold more than one {@link UserRole} (the existing role-assignment model already
 * supports this — {@code AuthorizationInterceptor} itself checks role-set intersection, not a
 * single role). Clearance for a caller is the <em>maximum</em> across all roles they hold —
 * standard RBAC union semantics, consistent with how role-based action permission already works
 * in this codebase.
 *
 * <p>Every method is fail-closed: an unknown/unparseable stored classification value is never
 * permitted regardless of role, and an empty role set is never permitted anything.
 */
public interface ClassificationPolicyPort {

    /**
     * Whether a caller holding {@code roles} may access a document whose stored classification is
     * {@code storedClassification} (the raw persisted string — may be {@code null}, blank, or
     * unparseable). Used for single-document checks (get, delete).
     */
    boolean isPermitted(Set<UserRole> roles, String storedClassification);

    /**
     * The maximum classification level (see {@link DocumentClassification#level()}) a caller
     * holding {@code roles} may access — the highest clearance among all held roles.
     */
    int maxClearanceLevel(Set<UserRole> roles);
}
