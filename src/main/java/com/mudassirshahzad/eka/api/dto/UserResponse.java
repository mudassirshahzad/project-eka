package com.mudassirshahzad.eka.api.dto;

import com.mudassirshahzad.eka.domain.user.User;
import com.mudassirshahzad.eka.domain.user.UserRole;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID          id,
        String        email,
        Set<UserRole> roles,
        boolean       active,
        Instant       createdAt
) {

    public UserResponse {
        Objects.requireNonNull(id,        "id must not be null");
        Objects.requireNonNull(email,     "email must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId().value(),
                user.getEmail(),
                user.getRoles(),
                user.isActive(),
                user.getCreatedAt());
    }
}
