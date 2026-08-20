package com.fareflow.ledger;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * The only class permitted to write ledger entries.
 *
 * <p>A single writer means the invariants — sign matches type, append-only, trip
 * reference present when required — are enforced in one place. If every service
 * could insert entries, those rules would live in five places, which in practice
 * means three.
 *
 * <p>There is no update or delete method here, and there never will be.
 */
@Service
@Transactional(readOnly = true)
public class LedgerService {

    private final LedgerRepository ledgerRepository;

    public LedgerService(LedgerRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    /**
     * Records a charge for a trip.
     *
     * <p>{@code MANDATORY} propagation: this must run inside a caller's transaction.
     * A charge written on its own, outside the transaction that created the trip,
     * is exactly the inconsistency the design exists to prevent — so the framework
     * refuses rather than trusting the caller to remember.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public LedgerEntry recordTripCharge(long userId, long tripId, long fareCents,
                                        String description, Instant occurredAt) {
        return ledgerRepository.save(
                LedgerEntry.tripCharge(userId, tripId, fareCents, description, occurredAt));
    }

    /**
     * Records a refund. The original charge is left untouched — both rows remain
     * visible with their own timestamps.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public LedgerEntry recordRefund(long userId, long tripId, long amountCents,
                                    String description, Instant occurredAt) {
        return ledgerRepository.save(
                LedgerEntry.refund(userId, tripId, amountCents, description, occurredAt));
    }

    @Transactional
    public LedgerEntry recordFareAdjustment(long userId, Long tripId, long signedAmountCents,
                                            String description, Instant occurredAt) {
        return ledgerRepository.save(
                LedgerEntry.fareAdjustment(userId, tripId, signedAmountCents, description, occurredAt));
    }

    /** Net movement in the window. Negative means money went out. */
    public long netAmountBetween(long userId, Instant start, Instant end) {
        return ledgerRepository.sumAmountBetween(userId, start, end);
    }

    public Page<LedgerEntry> findForUser(long userId, Pageable pageable) {
        return ledgerRepository.findByUserIdOrderByOccurredAtDescIdDesc(userId, pageable);
    }

    public List<LedgerEntry> findForTrip(long tripId) {
        return ledgerRepository.findByTripIdOrderByIdAsc(tripId);
    }

    public long countForUser(long userId) {
        return ledgerRepository.countByUserId(userId);
    }
}
