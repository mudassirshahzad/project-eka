package com.mudassirshahzad.eka.api.controller;

import com.mudassirshahzad.eka.api.dto.LoginRequest;
import com.mudassirshahzad.eka.api.dto.LoginResponse;
import com.mudassirshahzad.eka.api.security.JwtProperties;
import com.mudassirshahzad.eka.api.security.JwtTokenProvider;
import com.mudassirshahzad.eka.application.user.AuthenticateUserCommand;
import com.mudassirshahzad.eka.application.user.AuthenticateUserUseCase;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sole token-issuing entry point for P05.2 (ADR A02) — verify credentials, mint an access token,
 * return it. No refresh/logout lifecycle yet; every other endpoint in this API requires the
 * token this issues (see {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticateUserUseCase authenticateUserUseCase;
    private final JwtTokenProvider         jwtTokenProvider;
    private final JwtProperties            jwtProperties;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        User user = authenticateUserUseCase.execute(new AuthenticateUserCommand(
                request.email(), request.password(), TenantId.of(request.tenantId())));

        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getTenantId(), user.getRoles());

        return LoginResponse.bearer(accessToken, jwtProperties.accessTokenExpiryMs());
    }
}
