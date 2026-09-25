package com.mudassirshahzad.eka.api.controller;

import com.mudassirshahzad.eka.api.dto.LoginRequest;
import com.mudassirshahzad.eka.api.dto.LoginResponse;
import com.mudassirshahzad.eka.api.dto.RefreshTokenRequest;
import com.mudassirshahzad.eka.api.security.JwtAuthenticationToken;
import com.mudassirshahzad.eka.api.security.JwtProperties;
import com.mudassirshahzad.eka.api.security.JwtTokenProvider;
import com.mudassirshahzad.eka.api.security.LoginRateLimiter;
import com.mudassirshahzad.eka.application.auth.IssuedSession;
import com.mudassirshahzad.eka.application.auth.SessionApplicationService;
import com.mudassirshahzad.eka.application.user.AuthenticateUserCommand;
import com.mudassirshahzad.eka.application.user.AuthenticateUserUseCase;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sole token-issuing entry point. Verifies credentials, opens a session, and mints the access
 * token bound to it; {@code /refresh} rotates that session and {@code /logout} revokes it.
 *
 * <p>{@link LoginRateLimiter} runs first on login, keyed by source IP (v0.6.1, ADR EX05) — before
 * any credential check, so a caller past the limit never reaches {@link AuthenticateUserUseCase}
 * at all, and the dummy-hash timing-equalization work (ADR A06) never runs needlessly under a
 * brute-force burst.
 *
 * <p>Minting stays here rather than in the application layer (ADR A03, unchanged by WP-2):
 * {@link SessionApplicationService} decides whether a session may exist, and only
 * {@link JwtTokenProvider} encodes that decision as a JWT.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticateUserUseCase   authenticateUserUseCase;
    private final SessionApplicationService sessionApplicationService;
    private final JwtTokenProvider          jwtTokenProvider;
    private final JwtProperties             jwtProperties;
    private final LoginRateLimiter          loginRateLimiter;

    @PostMapping("/login")
    public LoginResponse login(HttpServletRequest servletRequest, @Valid @RequestBody LoginRequest request) {
        loginRateLimiter.checkAllowed(servletRequest.getRemoteAddr());

        User user = authenticateUserUseCase.execute(new AuthenticateUserCommand(
                request.email(), request.password(), TenantId.of(request.tenantId())));

        return toResponse(sessionApplicationService.issue(user));
    }

    /**
     * Exchanges a valid refresh token for a new access/refresh pair, rotating the session so the
     * presented secret becomes single-use (ADR RT02). Public, like {@code /login}: the caller's
     * access token has typically already expired, which is the whole reason they are here.
     */
    @PostMapping("/refresh")
    public LoginResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return toResponse(sessionApplicationService.rotate(request.refreshToken()));
    }

    /**
     * Revokes the caller's current session, immediately invalidating both its refresh token and
     * every access token issued under it. Requires authentication — the session to revoke is taken
     * from the caller's own validated token, never from the request body, so one caller can never
     * log another out.
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(Authentication authentication) {
        JwtAuthenticationToken token = (JwtAuthenticationToken) authentication;
        sessionApplicationService.revoke(token.sessionId());
    }

    private LoginResponse toResponse(IssuedSession session) {
        String accessToken = jwtTokenProvider.generateAccessToken(
                session.userId(), session.tenantId(), session.roles(), session.sessionId());

        return LoginResponse.bearer(
                accessToken,
                jwtProperties.accessTokenExpiryMs(),
                session.rawRefreshToken(),
                session.refreshTokenExpiresInMs());
    }
}
