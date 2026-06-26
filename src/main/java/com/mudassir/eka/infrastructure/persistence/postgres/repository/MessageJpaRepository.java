package com.mudassir.eka.infrastructure.persistence.postgres.repository;

import com.mudassir.eka.infrastructure.persistence.postgres.entity.MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageJpaRepository extends JpaRepository<MessageEntity, UUID> {

    @Query("""
            SELECT m FROM MessageEntity m
            WHERE m.conversation.id = :conversationId
            ORDER BY m.createdAt ASC
            """)
    List<MessageEntity> findByConversationIdOrderByCreatedAt(@Param("conversationId") UUID conversationId);

    @Query("""
            SELECT m FROM MessageEntity m
            WHERE m.conversation.id = :conversationId
            ORDER BY m.createdAt DESC
            LIMIT :limit
            """)
    List<MessageEntity> findRecentByConversationId(@Param("conversationId") UUID conversationId,
                                                    @Param("limit") int limit);
}
