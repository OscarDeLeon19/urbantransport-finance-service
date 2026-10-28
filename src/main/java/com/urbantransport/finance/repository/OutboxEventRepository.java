package com.urbantransport.finance.repository;

import com.urbantransport.finance.domain.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    List<OutboxEventEntity> findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();

    @Modifying
    @Transactional
    @Query("update OutboxEventEntity e set e.publishedAt = :now, e.attempts = e.attempts + 1, e.lastError = null where e.id = :id")
    void markPublished(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Transactional
    @Query("update OutboxEventEntity e set e.attempts = e.attempts + 1, e.lastError = :error where e.id = :id")
    void markFailed(@Param("id") UUID id, @Param("error") String error);
}
