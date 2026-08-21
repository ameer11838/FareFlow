package com.fareflow.payment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, UUID> {
    Optional<PaymentIntent> findByUserIdAndIdempotencyKey(long userId, String idempotencyKey);
    Optional<PaymentIntent> findByUserIdAndId(long userId, UUID id);
    Optional<PaymentIntent> findByTripId(long tripId);
    Page<PaymentIntent> findByUserIdOrderByCreatedAtDesc(long userId, Pageable pageable);
    long countByStatus(PaymentStatus status);
}
