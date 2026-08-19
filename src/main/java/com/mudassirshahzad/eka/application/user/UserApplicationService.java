package com.mudassirshahzad.eka.application.user;

import com.mudassirshahzad.eka.application.event.UserActivatedEvent;
import com.mudassirshahzad.eka.application.event.UserDeactivatedEvent;
import com.mudassirshahzad.eka.application.event.UserRegisteredEvent;
import com.mudassirshahzad.eka.application.shared.DomainEventPublisher;
import com.mudassirshahzad.eka.application.shared.DuplicateResourceException;
import com.mudassirshahzad.eka.application.shared.ResourceNotFoundException;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.User;
import com.mudassirshahzad.eka.domain.user.UserId;
import com.mudassirshahzad.eka.domain.user.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * {@code getUser}/{@code activateUser}/{@code deactivateUser} verify tenant ownership after
 * fetch (P06.1, ADR PC02) — {@link UserRepository#findById(UserId)} has no tenant-scoped variant,
 * so before this fix a caller only needed a valid {@code UserId} to read or deactivate a user in
 * <em>any</em> tenant; {@code activateUser}/{@code deactivateUser} already accepted a
 * {@code tenantId} parameter but never verified it against the fetched user. Mirrors the exact
 * pattern {@code ConversationApplicationService.requireTenantMatch} established (ADR TN01): a
 * mismatch resolves to the same 404 a nonexistent id would, never a distinct "forbidden" outcome.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserApplicationService {

    private final UserRepository       userRepository;
    private final DomainEventPublisher eventPublisher;
    private final MeterRegistry        meterRegistry;

    public User registerUser(RegisterUserCommand cmd) {
        if (userRepository.existsByEmailAndTenantId(cmd.email(), cmd.tenantId())) {
            throw new DuplicateResourceException(
                    "User with email '" + cmd.email() + "' already exists in this tenant");
        }
        User user = User.create(cmd.tenantId(), cmd.email(), cmd.passwordHash(), cmd.roles());
        User saved = userRepository.save(user);
        log.info("User registered: id={} email={} tenant={}",
                saved.getId(), saved.getEmail(), saved.getTenantId());
        eventPublisher.publish(new UserRegisteredEvent(saved.getId(), saved.getTenantId(), saved.getEmail()));
        return saved;
    }

    @Transactional(readOnly = true)
    public User getUser(UserId id, TenantId tenantId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id.value().toString()));
        return requireTenantMatch(user, tenantId);
    }

    @Transactional(readOnly = true)
    public Optional<User> findUserByEmail(String email, TenantId tenantId) {
        return userRepository.findByEmailAndTenantId(email, tenantId);
    }

    public boolean tenantHasAnyUser(TenantId tenantId) {
        return userRepository.existsByTenantId(tenantId);
    }

    public User activateUser(UserId id, TenantId tenantId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id.value().toString()));
        requireTenantMatch(user, tenantId);
        user.activate();
        User saved = userRepository.save(user);
        eventPublisher.publish(new UserActivatedEvent(saved.getId(), tenantId));
        return saved;
    }

    public User deactivateUser(UserId id, TenantId tenantId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id.value().toString()));
        requireTenantMatch(user, tenantId);
        user.deactivate();
        User saved = userRepository.save(user);
        log.info("User deactivated: id={} tenant={}", saved.getId(), tenantId);
        eventPublisher.publish(new UserDeactivatedEvent(saved.getId(), tenantId));
        return saved;
    }

    private User requireTenantMatch(User user, TenantId tenantId) {
        if (!user.getTenantId().equals(tenantId)) {
            log.warn("Tenant mismatch on user access: user={} requestedTenant={} actualTenant={}",
                    user.getId(), tenantId, user.getTenantId());
            meterRegistry.counter("eka.authz.failures", "reason", "ownership").increment();
            throw new ResourceNotFoundException("User", user.getId().value().toString());
        }
        return user;
    }

    public User changePassword(ChangePasswordCommand cmd) {
        User user = userRepository.findById(cmd.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", cmd.userId().value().toString()));
        user.changePassword(cmd.newPasswordHash());
        return userRepository.save(user);
    }

    public User assignRole(AssignRoleCommand cmd) {
        User user = userRepository.findById(cmd.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", cmd.userId().value().toString()));
        user.addRole(cmd.role());
        return userRepository.save(user);
    }

    public User removeRole(RemoveRoleCommand cmd) {
        User user = userRepository.findById(cmd.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", cmd.userId().value().toString()));
        user.removeRole(cmd.role());
        return userRepository.save(user);
    }
}
