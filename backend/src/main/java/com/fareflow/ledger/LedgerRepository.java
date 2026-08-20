package com.fareflow.ledger;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface LedgerRepository extends JpaRepository<LedgerEntry, Long> {

    /**
     * Net movement over a window. Normally negative (money out). The caller
     * negates it to get "spent".
     *
     * <p>COALESCE so an empty window returns 0 rather than null.
     */
    @Query("""
            SELECT COALESCE(SUM(e.amountCents), 0) FROM LedgerEntry e
            WHERE e.userId = :userId
              AND e.occurredAt >= :start
              AND e.occurredAt < :end
            """)
    long sumAmountBetween(@Param("userId") long userId,
                          @Param("start") Instant start,
                          @Param("end") Instant end);

    Page<LedgerEntry> findByUserIdOrderByOccurredAtDescIdDesc(long userId, Pageable pageable);

    @Query("""
            SELECT e FROM LedgerEntry e
            WHERE e.userId = :userId
              AND e.occurredAt >= :start
              AND e.occurredAt < :end
            ORDER BY e.occurredAt DESC, e.id DESC
            """)
    List<LedgerEntry> findByUserIdBetween(@Param("userId") long userId,
                                          @Param("start") Instant start,
                                          @Param("end") Instant end);

    List<LedgerEntry> findByTripIdOrderByIdAsc(long tripId);

    long countByUserId(long userId);
}
