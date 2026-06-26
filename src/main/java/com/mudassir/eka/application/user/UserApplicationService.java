package com.mudassir.eka.application.user;

import com.mudassir.eka.application.event.UserActivatedEvent;
import com.mudassir.eka.application.event.UserDeactivatedEvent;
import com.mudassir.eka.application.event.UserRegisteredEvent;
import com.mudassir.eka.application.shared.DomainEventPublisher;
import com.mudassir.eka.application.shared.DuplicateResourceException;
import com.mudassir.eka.application.shared.ResourceNotFoundException;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.User;
import com.mudassir.eka.domain.user.UserId;
import com.mudassir.eka.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserApplicationService {

    private final UserRepository       userRepository;
    private final DomainEventPublisher eventPublisher;

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
    public User getUser(UserId id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id.value().toString()));
    }

    @Transactional(readOnly = true)
    public Optional<User> findUserByEmail(String email, TenantId tenantId) {
        return userRepository.findByEmailAndTenantId(email, tenantId);
    }

    public User activateUser(UserId id, TenantId tenantId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id.value().toString()));
        user.activate();
        User saved = userRepository.save(user);
        eventPublisher.publish(new UserActivatedEvent(saved.getId(), tenantId));
        return saved;
    }

    public User deactivateUser(UserId id, TenantId tenantId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id.value().toString()));
        user.deactivate();
        User saved = userRepository.save(user);
        log.info("User deactivated: id={} tenant={}", saved.getId(), tenantId);
        eventPublisher.publish(new UserDeactivatedEvent(saved.getId(), tenantId));
        return saved;
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
