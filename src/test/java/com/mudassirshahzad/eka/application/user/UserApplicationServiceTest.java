package com.mudassirshahzad.eka.application.user;

import com.mudassirshahzad.eka.application.shared.DomainEventPublisher;
import com.mudassirshahzad.eka.application.shared.ResourceNotFoundException;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.User;
import com.mudassirshahzad.eka.domain.user.UserRepository;
import com.mudassirshahzad.eka.domain.user.UserRole;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Focused on the tenant check added to {@code getUser}/{@code activateUser}/{@code deactivateUser}
 * (P06.1, ADR PC02) — before this fix, {@link UserRepository#findById} had no tenant-scoped
 * variant, so any caller with a valid {@code UserId} could read or deactivate a user in any
 * tenant regardless of the {@code tenantId} argument already being passed in.
 */
@ExtendWith(MockitoExtension.class)
class UserApplicationServiceTest {

    @Mock private UserRepository       userRepository;
    @Mock private DomainEventPublisher eventPublisher;

    private SimpleMeterRegistry    meterRegistry;
    private UserApplicationService service;

    private final TenantId tenantId      = TenantId.generate();
    private final TenantId otherTenantId = TenantId.generate();

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new UserApplicationService(userRepository, eventPublisher, meterRegistry);
    }

    private User userInTenant(TenantId ownerTenant) {
        return User.create(ownerTenant, "user@example.com", "hashed", EnumSet.of(UserRole.USER));
    }

    @Test
    void getUser_wrongTenant_throwsResourceNotFoundException() {
        User user = userInTenant(tenantId);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.getUser(user.getId(), otherTenantId));
    }

    @Test
    void getUser_correctTenant_returnsUser() {
        User user = userInTenant(tenantId);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        User result = service.getUser(user.getId(), tenantId);

        assertThat(result).isSameAs(user);
    }

    @Test
    void deactivateUser_wrongTenant_throwsResourceNotFoundException() {
        User user = userInTenant(tenantId);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.deactivateUser(user.getId(), otherTenantId));
    }

    @Test
    void deactivateUser_correctTenant_deactivates() {
        User user = userInTenant(tenantId);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User result = service.deactivateUser(user.getId(), tenantId);

        assertThat(result.isActive()).isFalse();
    }

    @Test
    void activateUser_wrongTenant_throwsResourceNotFoundException() {
        User user = userInTenant(tenantId);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.activateUser(user.getId(), otherTenantId));
    }

    @Test
    void tenantHasAnyUser_delegatesToRepository() {
        when(userRepository.existsByTenantId(tenantId)).thenReturn(true);

        assertThat(service.tenantHasAnyUser(tenantId)).isTrue();
    }
}
