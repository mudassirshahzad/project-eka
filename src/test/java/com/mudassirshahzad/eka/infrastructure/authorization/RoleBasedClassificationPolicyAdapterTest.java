package com.mudassirshahzad.eka.infrastructure.authorization;

import com.mudassirshahzad.eka.domain.document.DocumentClassification;
import com.mudassirshahzad.eka.domain.user.UserRole;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class RoleBasedClassificationPolicyAdapterTest {

    private final RoleBasedClassificationPolicyAdapter policy = new RoleBasedClassificationPolicyAdapter();

    @Test
    void maxClearanceLevel_viewer_isInternal() {
        assertThat(policy.maxClearanceLevel(Set.of(UserRole.VIEWER)))
                .isEqualTo(DocumentClassification.INTERNAL.level());
    }

    @Test
    void maxClearanceLevel_auditor_isConfidential() {
        assertThat(policy.maxClearanceLevel(Set.of(UserRole.AUDITOR)))
                .isEqualTo(DocumentClassification.CONFIDENTIAL.level());
    }

    @Test
    void maxClearanceLevel_user_isConfidential() {
        assertThat(policy.maxClearanceLevel(Set.of(UserRole.USER)))
                .isEqualTo(DocumentClassification.CONFIDENTIAL.level());
    }

    @Test
    void maxClearanceLevel_admin_isRestricted() {
        assertThat(policy.maxClearanceLevel(Set.of(UserRole.ADMIN)))
                .isEqualTo(DocumentClassification.RESTRICTED.level());
    }

    @Test
    void maxClearanceLevel_multipleRoles_takesTheHighest() {
        assertThat(policy.maxClearanceLevel(Set.of(UserRole.VIEWER, UserRole.ADMIN)))
                .isEqualTo(DocumentClassification.RESTRICTED.level());
    }

    @Test
    void maxClearanceLevel_emptyRoleSet_grantsNoClearance() {
        assertThat(policy.maxClearanceLevel(Set.of())).isLessThan(DocumentClassification.PUBLIC.level());
    }

    @Test
    void maxClearanceLevel_rejectsNullRoleSet() {
        assertThatNullPointerException().isThrownBy(() -> policy.maxClearanceLevel(null));
    }

    @Test
    void isPermitted_documentAtOrBelowClearance_isTrue() {
        assertThat(policy.isPermitted(Set.of(UserRole.VIEWER), "PUBLIC")).isTrue();
        assertThat(policy.isPermitted(Set.of(UserRole.VIEWER), "INTERNAL")).isTrue();
    }

    @Test
    void isPermitted_documentAboveClearance_isFalse() {
        assertThat(policy.isPermitted(Set.of(UserRole.VIEWER), "CONFIDENTIAL")).isFalse();
        assertThat(policy.isPermitted(Set.of(UserRole.VIEWER), "RESTRICTED")).isFalse();
    }

    @Test
    void isPermitted_restrictedDocument_onlyAdminClearance() {
        assertThat(policy.isPermitted(Set.of(UserRole.ADMIN), "RESTRICTED")).isTrue();
        assertThat(policy.isPermitted(Set.of(UserRole.USER), "RESTRICTED")).isFalse();
        assertThat(policy.isPermitted(Set.of(UserRole.AUDITOR), "RESTRICTED")).isFalse();
    }

    @Test
    void isPermitted_nullClassification_isDeniedEvenForAdmin() {
        assertThat(policy.isPermitted(Set.of(UserRole.ADMIN), null)).isFalse();
    }

    @Test
    void isPermitted_unknownClassification_isDeniedEvenForAdmin() {
        assertThat(policy.isPermitted(Set.of(UserRole.ADMIN), "NOT_A_REAL_TIER")).isFalse();
    }

    @Test
    void isPermitted_everyRole_coversEveryClassification() {
        for (UserRole role : EnumSet.allOf(UserRole.class)) {
            for (DocumentClassification tier : DocumentClassification.values()) {
                boolean expected = tier.level() <= policy.maxClearanceLevel(Set.of(role));
                assertThat(policy.isPermitted(Set.of(role), tier.name()))
                        .as("role=%s classification=%s", role, tier)
                        .isEqualTo(expected);
            }
        }
    }
}
