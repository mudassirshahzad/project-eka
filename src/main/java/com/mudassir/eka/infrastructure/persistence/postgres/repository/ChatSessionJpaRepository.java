package com.mudassir.eka.infrastructure.persistence.postgres.repository;

import com.mudassir.eka.infrastructure.persistence.postgres.entity.ChatSessionEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.ConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatSessionJpaRepository extends JpaRepository<ChatSessionEntity, UUID> {

    Optional<ChatSessionEntity> findByConversationAndStatus(
            ConversationEntity conversation, String status);

    List<ChatSessionEntity> findByConversationOrderByStartedAtAsc(
            ConversationEntity conversation);
}
