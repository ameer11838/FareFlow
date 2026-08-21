package com.fareflow.trip;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface TripRepository extends JpaRepository<Trip, Long> {

    Page<Trip> findByUserIdOrderByTakenAtDescIdDesc(long userId, Pageable pageable);

    List<Trip> findTop5ByUserIdOrderByTakenAtDescIdDesc(long userId);

    @Query("""
            SELECT COUNT(t) FROM Trip t
            WHERE t.userId = :userId
              AND t.status = com.fareflow.trip.TripStatus.COMPLETED
              AND t.takenAt >= :start
              AND t.takenAt < :end
            """)
    long countCompletedBetween(@Param("userId") long userId,
                               @Param("start") Instant start,
                               @Param("end") Instant end);

    /**
     * Total "saved vs. fastest route" for completed trips in the window.
     *
     * <p>Trips with a NULL baseline are excluded by the WHERE clause, so they
     * contribute nothing rather than being counted as zero. Returns null when no
     * trip in the window has a baseline at all — the caller turns that into a
     * null savings figure rather than displaying $0.00.
     */
    @Query("""
            SELECT SUM(t.baselineFareCents - t.fareCents) FROM Trip t
            WHERE t.userId = :userId
              AND t.status = com.fareflow.trip.TripStatus.COMPLETED
              AND t.baselineFareCents IS NOT NULL
              AND t.takenAt >= :start
              AND t.takenAt < :end
            """)
    Long sumSavingsBetween(@Param("userId") long userId,
                           @Param("start") Instant start,
                           @Param("end") Instant end);

    /**
     * Per-provider spend and usage for completed trips in a window.
     *
     * <p>Returns rows only for providers actually used, so the Insights page can
     * never show a provider the user has not travelled with.
     */
    @Query("""
            SELECT t.provider AS provider,
                   COUNT(t) AS tripCount,
                   SUM(t.fareCents) AS totalFareCents,
                   AVG(t.fareCents) AS averageFareCents,
                   AVG(t.durationMinutes) AS averageDurationMinutes,
                   MIN(t.fareCents) AS minFareCents,
                   MIN(t.durationMinutes) AS minDurationMinutes
            FROM Trip t
            WHERE t.userId = :userId
              AND t.status = com.fareflow.trip.TripStatus.COMPLETED
              AND t.takenAt >= :start AND t.takenAt < :end
            GROUP BY t.provider
            ORDER BY SUM(t.fareCents) DESC
            """)
    List<ProviderUsage> findProviderUsageBetween(@Param("userId") long userId,
                                                 @Param("start") Instant start,
                                                 @Param("end") Instant end);

    /** Projection for the per-provider breakdown. */
    interface ProviderUsage {
        String getProvider();
        long getTripCount();
        long getTotalFareCents();
        double getAverageFareCents();
        double getAverageDurationMinutes();
        long getMinFareCents();
        int getMinDurationMinutes();
    }

    /** Total minutes travelled on completed trips, for the time-traded figure. */
    @Query("""
            SELECT COALESCE(SUM(t.durationMinutes), 0) FROM Trip t
            WHERE t.userId = :userId
              AND t.status = com.fareflow.trip.TripStatus.COMPLETED
              AND t.takenAt >= :start AND t.takenAt < :end
            """)
    long sumDurationBetween(@Param("userId") long userId,
                            @Param("start") Instant start,
                            @Param("end") Instant end);

    /**
     * Extra minutes spent versus always taking the fastest route, for trips where a
     * baseline exists. Null when nothing is comparable.
     */
    @Query("""
            SELECT SUM(t.durationMinutes - f.durationMinutes) FROM Trip t
            JOIN TransitRoute f ON f.origin = t.origin AND f.destination = t.destination
            WHERE t.userId = :userId
              AND t.status = com.fareflow.trip.TripStatus.COMPLETED
              AND t.baselineFareCents IS NOT NULL
              AND f.active = true
              AND f.fareCents = t.baselineFareCents
              AND t.takenAt >= :start AND t.takenAt < :end
            """)
    Long sumMinutesTradedBetween(@Param("userId") long userId,
                                 @Param("start") Instant start,
                                 @Param("end") Instant end);

    /** Earliest completed trip in the window, used to size the history sample. */
    @Query("""
            SELECT MIN(t.takenAt) FROM Trip t
            WHERE t.userId = :userId
              AND t.status = com.fareflow.trip.TripStatus.COMPLETED
              AND t.takenAt >= :since
            """)
    Instant findEarliestTripAt(@Param("userId") long userId, @Param("since") Instant since);

    /**
     * Test-only: moves a trip's business time so a suite can build a realistic
     * multi-week history without waiting weeks. Never called by application code —
     * {@code taken_at} is immutable in every production path.
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Trip t SET t.takenAt = :takenAt WHERE t.id = :tripId")
    @org.springframework.transaction.annotation.Transactional
    void backdateForTesting(@Param("tripId") long tripId, @Param("takenAt") Instant takenAt);

    /**
     * Every completed trip in a window, oldest first.
     *
     * <p>Feeds the history endpoints, which bucket by day, week, or month in Java
     * rather than in SQL. Date truncation is timezone-dependent and the rider's
     * zone lives on the user row, not in the database session — bucketing in the
     * service keeps one timezone rule instead of two.
     */
    @Query("""
            SELECT t FROM Trip t
            WHERE t.userId = :userId
              AND t.status = com.fareflow.trip.TripStatus.COMPLETED
              AND t.takenAt >= :start AND t.takenAt < :end
            ORDER BY t.takenAt ASC, t.id ASC
            """)
    List<Trip> findCompletedBetween(@Param("userId") long userId,
                                    @Param("start") Instant start,
                                    @Param("end") Instant end);

    /** Business time of the rider's very first completed trip, or null if none. */
    @Query("""
            SELECT MIN(t.takenAt) FROM Trip t
            WHERE t.userId = :userId
              AND t.status = com.fareflow.trip.TripStatus.COMPLETED
            """)
    Instant findFirstCompletedTripAt(@Param("userId") long userId);

    /** Used to return the original result for a repeated idempotency key. */
    java.util.Optional<Trip> findByUserIdAndIdempotencyKey(long userId, String idempotencyKey);

    long countByUserId(long userId);
}
