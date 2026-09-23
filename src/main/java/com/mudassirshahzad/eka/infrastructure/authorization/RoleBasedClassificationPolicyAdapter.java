package com.mudassirshahzad.eka.infrastructure.authorization;

import com.mudassirshahzad.eka.domain.document.ClassificationPolicyPort;
import com.mudassirshahzad.eka.domain.document.DocumentClassification;
import com.mudassirshahzad.eka.domain.user.UserRole;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Sole {@link ClassificationPolicyPort} implementation (Phase 6, P06.2). A fixed, in-code
 * role → minimum-clearance mapping — four roles, no configurability was asked for, so this is
 * deliberately not a database table or externalized policy engine (mirrors the "no unnecessary
 * permission frameworks" judgment already made for {@code AuthorizationInterceptor}'s role policy,
 * ADR AZ02).
 *
 * <p>A role's clearance is the highest {@link DocumentClassification} it may access; every tier
 * at or below that level is also permitted. A caller's overall clearance is the maximum across
 * every role they hold. An unknown/unparseable stored classification always resolves to
 * {@link DocumentClassification#UNKNOWN_LEVEL}, which no clearance can satisfy — fail-closed for
 * every role, including {@code ADMIN}.
 */
@Component
public class RoleBasedClassificationPolicyAdapter implements ClassificationPolicyPort {

    private static final Map<UserRole, DocumentClassification> CLEARANCE = Map.of(
            UserRole.VIEWER,  DocumentClassification.INTERNAL,
            UserRole.AUDITOR, DocumentClassification.CONFIDENTIAL,
            UserRole.USER,    DocumentClassification.CONFIDENTIAL,
            UserRole.ADMIN,   DocumentClassification.RESTRICTED
    );

    /** No role at all clears nothing — fail-closed, never "no restriction." */
    private static final int NO_CLEARANCE = -1;

    @Override
    public boolean isPermitted(Set<UserRole> roles, String storedClassification) {
        int documentLevel = DocumentClassification.levelOf(storedClassification);
        return documentLevel <= maxClearanceLevel(roles);
    }

    @Override
    public int maxClearanceLevel(Set<UserRole> roles) {
        Objects.requireNonNull(roles, "roles must not be null");
        return roles.stream()
                .map(role -> CLEARANCE.getOrDefault(role, null))
                .filter(Objects::nonNull)
                .mapToInt(DocumentClassification::level)
                .max()
                .orElse(NO_CLEARANCE);
    }
}
