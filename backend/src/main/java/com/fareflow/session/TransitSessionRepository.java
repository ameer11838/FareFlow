package com.fareflow.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransitSessionRepository extends JpaRepository<TransitSession, UUID> {
    Optional<TransitSession> findByUserIdAndId(long userId, UUID id);
    Optional<TransitSession> findByUserIdAndIdempotencyKey(long userId, String idempotencyKey);
    Optional<TransitSession> findFirstByUserIdAndStatusInOrderByStartedAtDesc(
            long userId, List<TransitSessionStatus> statuses);
}
