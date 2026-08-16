package com.mudassirshahzad.eka.application.user;

import com.mudassirshahzad.eka.application.shared.InvalidCredentialsException;
import com.mudassirshahzad.eka.domain.user.User;
import com.mudassirshahzad.eka.domain.user.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Verifies login credentials and returns the authenticated {@link User} — nothing else. Token
 * issuance is deliberately not this use case's concern (ADR A03): {@code api.security.JwtTokenProvider}
 * is the only class that turns an identity into a JWT, keeping this application-layer use case
 * technology-agnostic, exactly like every other use case in this codebase.
 *
 * <p>Every rejected login increments {@code eka.auth.failures{type=credentials}} (P05.4, ADR OB02)
 * — the same counter name {@code JwtAuthenticationFilter} uses for bad tokens, differing only by
 * the {@code type} tag, so operators see one metric for total authentication-failure volume.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthenticateUserUseCase {

    /**
     * A syntactically valid but otherwise meaningless BCrypt hash, compared against for every
     * unknown-email login attempt. Without this, an unknown email would skip BCrypt entirely
     * while a known email with the wrong password would still pay BCrypt's cost — a timing
     * side channel that lets an attacker enumerate valid emails/tenants even though every path
     * throws the identical {@link InvalidCredentialsException}.
     */
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MeterRegistry   meterRegistry;

    /**
     * @throws InvalidCredentialsException for an unknown email/tenant, an inactive account, or a
     *                                      wrong password — always the same exception, so a caller
     *                                      cannot distinguish "no such user" from "wrong password"
     *                                      by response content or, thanks to {@link #DUMMY_PASSWORD_HASH},
     *                                      by response timing either
     */
    public User execute(AuthenticateUserCommand cmd) {
        Optional<User> found = userRepository.findByEmailAndTenantId(cmd.email(), cmd.tenantId());

        boolean passwordMatches = passwordEncoder.matches(
                cmd.rawPassword(), found.map(User::getPasswordHash).orElse(DUMMY_PASSWORD_HASH));

        if (found.isEmpty() || !found.get().isActive() || !passwordMatches) {
            meterRegistry.counter("eka.auth.failures", "type", "credentials").increment();
            throw new InvalidCredentialsException();
        }

        return found.get();
    }
}
