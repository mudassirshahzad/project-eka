package com.mudassir.eka.application.user;

import com.mudassir.eka.application.shared.ApplicationException;
import com.mudassir.eka.domain.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RegisterUserUseCase {

    private final UserApplicationService userService;

    public User execute(RegisterUserCommand cmd) {
        Objects.requireNonNull(cmd, "command must not be null");
        Objects.requireNonNull(cmd.tenantId(), "tenantId must not be null");

        if (cmd.email() == null || cmd.email().isBlank()) {
            throw new ApplicationException("email must not be blank");
        }
        int atIndex = cmd.email().indexOf('@');
        if (atIndex <= 0 || atIndex >= cmd.email().length() - 1) {
            throw new ApplicationException("email must contain '@' with characters before and after");
        }
        if (cmd.passwordHash() == null || cmd.passwordHash().isBlank()) {
            throw new ApplicationException("passwordHash must not be blank");
        }
        if (cmd.roles() == null || cmd.roles().isEmpty()) {
            throw new ApplicationException("at least one role must be assigned");
        }

        log.debug("Registering user: email={} tenant={}", cmd.email(), cmd.tenantId());
        return userService.registerUser(cmd);
    }
}
