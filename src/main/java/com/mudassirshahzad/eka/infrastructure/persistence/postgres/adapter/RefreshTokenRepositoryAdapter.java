package com.mudassirshahzad.eka.infrastructure.persistence.postgres.adapter;

import com.mudassirshahzad.eka.domain.auth.RefreshToken;
import com.mudassirshahzad.eka.domain.auth.RefreshTokenRepository;
import com.mudassirshahzad.eka.domain.auth.SessionId;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.entity.RefreshTokenEntity;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.repository.RefreshTokenJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository jpaRepository;

    @Override
    @Transactional
    public RefreshToken save(RefreshToken domain) {
        RefreshTokenEntity entity = jpaRepository.findById(domain.getId().value())
                .map(existing -> {
                    // Revocation is the only mutation a session ever undergoes; every other column
                    // is declared non-updatable on the entity.
                    existing.setRevokedAt(domain.getRevokedAt());
                    return existing;
                })
                .orElseGet(() -> toEntity(domain));

        return toDomain(jpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshToken> findById(SessionId id) {
        return jpaRepository.findById(id.value()).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return jpaRepository.findByTokenHash(tokenHash).map(this::toDomain);
    }

    @Override
    @Transactional
    public int revokeAllForUser(UserId userId, Instant when) {
        return jpaRepository.revokeAllForUser(userId.value(), when);
    }

    @Override
    @Transactional
    public int deleteExpiredBefore(Instant cutoff) {
        return jpaRepository.deleteExpiredBefore(cutoff);
    }

    private RefreshTokenEntity toEntity(RefreshToken domain) {
        RefreshTokenEntity entity = RefreshTokenEntity.builder()
                .userId(domain.getUserId().value())
                .tenantId(domain.getTenantId().value())
                .tokenHash(domain.getTokenHash())
                .issuedAt(domain.getIssuedAt())
                .expiresAt(domain.getExpiresAt())
                .revokedAt(domain.getRevokedAt())
                .build();
        entity.setId(domain.getId().value());
        return entity;
    }

    private RefreshToken toDomain(RefreshTokenEntity entity) {
        return RefreshToken.reconstitute(
                SessionId.of(entity.getId()),
                UserId.of(entity.getUserId()),
                TenantId.of(entity.getTenantId()),
                entity.getTokenHash(),
                entity.getIssuedAt(),
                entity.getExpiresAt(),
                entity.getRevokedAt());
    }
}
