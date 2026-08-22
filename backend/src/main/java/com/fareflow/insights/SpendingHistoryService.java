package com.fareflow.insights;

import com.fareflow.insights.dto.SpendingHistoryResponse;
import com.fareflow.route.TransitProvider;
import com.fareflow.trip.Trip;
import com.fareflow.trip.TripRepository;
import com.fareflow.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Buckets a rider's real travel history for charting.
 *
 * <p>Aggregation happens here rather than in SQL because the bucket boundaries are
 * timezone-dependent and the timezone lives on the user row. Doing it in Java
 * keeps a single rule for what "a day" means, shared with {@code WeekWindow}.
 *
 * <p>The service adds nothing the trips table does not already contain. Empty
 * buckets stay empty, averages over zero trips stay null, and the series is
 * clipped so it never extends back past the rider's first trip.
 */
@Service
@Transactional(readOnly = true)
public class SpendingHistoryService {

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private final TripRepository tripRepository;
    private final Clock clock;

    public SpendingHistoryService(TripRepository tripRepository, Clock clock) {
        this.tripRepository = tripRepository;
        this.clock = clock;
    }

    public SpendingHistoryResponse history(User user, HistoryRange range) {
        ZoneId zone = user.zoneId();
        LocalDate today = LocalDate.ofInstant(clock.instant(), zone);

        // Align the window to whole buckets so no bar is a partial week or month;
        // a Monday-to-Wednesday bar drawn beside full weeks reads as a quiet week
        // rather than an incomplete one.
        LocalDate requestedStart = today.minusDays(range.days() - 1L);
        LocalDate windowStart = range.bucketStart(requestedStart);
        LocalDate windowEndExclusive = today.plusDays(1);

        Instant firstTripAt = tripRepository.findFirstCompletedTripAt(user.getId());
        LocalDate firstTripDate = firstTripAt == null ? null : LocalDate.ofInstant(firstTripAt, zone);

        // Never draw periods that predate the rider. A rider who joined on Tuesday
        // did not spend $0 for the eleven months before that -- those months simply
        // are not theirs to show.
        LocalDate seriesStart = windowStart;
        if (firstTripDate != null) {
            LocalDate firstBucket = range.bucketStart(firstTripDate);
            if (firstBucket.isAfter(seriesStart)) {
                seriesStart = firstBucket;
            }
        }

        Instant start = seriesStart.atStartOfDay(zone).toInstant();
        Instant end = windowEndExclusive.atStartOfDay(zone).toInstant();

        List<Trip> trips = tripRepository.findCompletedBetween(user.getId(), start, end);

        List<SpendingHistoryResponse.Bucket> buckets =
                firstTripDate == null ? List.of() : buildBuckets(range, zone, seriesStart, today, trips);

        return new SpendingHistoryResponse(
                range.code(),
                range.displayName(),
                range.granularity().name(),
                seriesStart,
                today,
                !trips.isEmpty(),
                firstTripDate,
                rangesWithData(user, zone, today),
                user.getWeeklyBudgetCents(),
                totals(trips),
                comparison(user, zone, seriesStart, today, trips),
                buckets,
                byOperator(trips),
                byMode(trips),
                mostUsedRoutes(trips));
    }

    // ---------------------------------------------------------------- buckets

    private List<SpendingHistoryResponse.Bucket> buildBuckets(HistoryRange range,
                                                              ZoneId zone,
                                                              LocalDate seriesStart,
                                                              LocalDate today,
                                                              List<Trip> trips) {
        // Every bucket in the window exists up front, so a gap in travel renders as
        // a zero-height bar in its correct position rather than collapsing the axis.
        Map<LocalDate, List<Trip>> grouped = new LinkedHashMap<>();
        for (LocalDate cursor = seriesStart; !cursor.isAfter(today); cursor = range.nextBucket(cursor)) {
            grouped.put(cursor, new ArrayList<>());
        }
        for (Trip trip : trips) {
            LocalDate bucket = range.bucketStart(LocalDate.ofInstant(trip.getTakenAt(), zone));
            List<Trip> slot = grouped.get(bucket);
            if (slot != null) {
                slot.add(trip);
            }
        }

        List<SpendingHistoryResponse.Bucket> buckets = new ArrayList<>(grouped.size());
        long running = 0;
        for (Map.Entry<LocalDate, List<Trip>> entry : grouped.entrySet()) {
            List<Trip> inBucket = entry.getValue();
            long spent = inBucket.stream().mapToLong(Trip::getFareCents).sum();
            running += spent;

            Long averageFare = inBucket.isEmpty() ? null : Math.round((double) spent / inBucket.size());
            Long averageDuration = inBucket.isEmpty() ? null : Math.round(
                    inBucket.stream().mapToInt(Trip::getDurationMinutes).average().orElse(0));

            buckets.add(new SpendingHistoryResponse.Bucket(
                    entry.getKey(),
                    label(range, entry.getKey()),
                    spent,
                    inBucket.size(),
                    averageFare,
                    averageDuration,
                    savings(inBucket),
                    running));
        }
        return buckets;
    }

    private static String label(HistoryRange range, LocalDate date) {
        return switch (range.granularity()) {
            case DAY -> DAY_LABEL.format(date);
            case WEEK -> "Week of " + DAY_LABEL.format(date);
            case MONTH -> MONTH_LABEL.format(date);
        };
    }

    // ----------------------------------------------------------------- totals

    private static SpendingHistoryResponse.Totals totals(List<Trip> trips) {
        long spent = trips.stream().mapToLong(Trip::getFareCents).sum();
        long minutes = trips.stream().mapToLong(Trip::getDurationMinutes).sum();
        List<Trip> distanceTrips = trips.stream()
                .filter(trip -> trip.getDistanceMetres() != null && trip.getDistanceMetres() > 0)
                .toList();
        long distanceMetres = distanceTrips.stream().mapToLong(Trip::getDistanceMetres).sum();
        long distanceSpend = distanceTrips.stream().mapToLong(Trip::getFareCents).sum();
        Long costPerMile = distanceMetres == 0 ? null : Math.round(
                distanceSpend / (distanceMetres / 1_609.344));
        return new SpendingHistoryResponse.Totals(
                spent,
                trips.size(),
                trips.isEmpty() ? null : Math.round((double) spent / trips.size()),
                trips.isEmpty() ? null : Math.round((double) minutes / trips.size()),
                savings(trips),
                minutes,
                distanceMetres == 0 ? null : distanceMetres,
                costPerMile,
                distanceTrips.size());
    }

    /**
     * Money not spent versus taking the fastest option, over trips that recorded a
     * baseline. Null — not zero — when nothing in the set is comparable.
     */
    private static Long savings(List<Trip> trips) {
        List<Long> comparable = trips.stream()
                .map(trip -> trip.savedVersusFastestCents().orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
        return comparable.isEmpty() ? null : comparable.stream().mapToLong(Long::longValue).sum();
    }

    // ------------------------------------------------------------- comparison

    /**
     * The equally long window immediately before this one.
     *
     * <p>Null when the rider had not started travelling by then: comparing this
     * month against a month that predates the account would report a 100% increase
     * from a period that never existed.
     */
    private SpendingHistoryResponse.Comparison comparison(User user,
                                                          ZoneId zone,
                                                          LocalDate seriesStart,
                                                          LocalDate today,
                                                          List<Trip> current) {
        long spanDays = Math.max(1, ChronoUnit.DAYS.between(seriesStart, today.plusDays(1)));

        LocalDate previousEndExclusive = seriesStart;
        LocalDate previousStart = previousEndExclusive.minusDays(spanDays);

        Instant firstTripAt = tripRepository.findFirstCompletedTripAt(user.getId());
        if (firstTripAt == null) {
            return null;
        }
        LocalDate firstTripDate = LocalDate.ofInstant(firstTripAt, zone);
        if (!firstTripDate.isBefore(previousEndExclusive)) {
            return null;
        }

        List<Trip> previous = tripRepository.findCompletedBetween(
                user.getId(),
                previousStart.atStartOfDay(zone).toInstant(),
                previousEndExclusive.atStartOfDay(zone).toInstant());

        long previousSpend = previous.stream().mapToLong(Trip::getFareCents).sum();
        long currentSpend = current.stream().mapToLong(Trip::getFareCents).sum();

        return new SpendingHistoryResponse.Comparison(
                previousStart,
                previousEndExclusive.minusDays(1),
                previousSpend,
                previous.size(),
                previous.isEmpty() ? null : Math.round((double) previousSpend / previous.size()),
                currentSpend - previousSpend,
                previousSpend > 0 ? (double) (currentSpend - previousSpend) / previousSpend : null);
    }

    // ------------------------------------------------------------ breakdowns

    private static List<SpendingHistoryResponse.OperatorSlice> byOperator(List<Trip> trips) {
        long total = trips.stream().mapToLong(Trip::getFareCents).sum();
        Map<String, List<Trip>> grouped = new LinkedHashMap<>();
        trips.forEach(trip -> grouped.computeIfAbsent(trip.getProvider(), key -> new ArrayList<>()).add(trip));

        return grouped.entrySet().stream()
                .map(entry -> {
                    long spent = entry.getValue().stream().mapToLong(Trip::getFareCents).sum();
                    return new SpendingHistoryResponse.OperatorSlice(
                            entry.getKey(),
                            displayNameOf(entry.getKey()),
                            entry.getValue().size(),
                            spent,
                            Math.round((double) spent / entry.getValue().size()),
                            total > 0 ? (double) spent / total : 0d);
                })
                .sorted(Comparator.comparingLong(SpendingHistoryResponse.OperatorSlice::spentCents).reversed())
                .toList();
    }

    private static List<SpendingHistoryResponse.ModeSlice> byMode(List<Trip> trips) {
        long total = trips.stream().mapToLong(Trip::getFareCents).sum();
        Map<String, List<Trip>> grouped = new LinkedHashMap<>();
        trips.forEach(trip -> grouped.computeIfAbsent(trip.getMode(), key -> new ArrayList<>()).add(trip));

        return grouped.entrySet().stream()
                .map(entry -> {
                    long spent = entry.getValue().stream().mapToLong(Trip::getFareCents).sum();
                    return new SpendingHistoryResponse.ModeSlice(
                            entry.getKey(),
                            titleCase(entry.getKey()),
                            entry.getValue().size(),
                            spent,
                            total > 0 ? (double) spent / total : 0d);
                })
                .sorted(Comparator.comparingLong(SpendingHistoryResponse.ModeSlice::spentCents).reversed())
                .toList();
    }

    private static List<SpendingHistoryResponse.RouteSlice> mostUsedRoutes(List<Trip> trips) {
        Map<String, List<Trip>> grouped = new LinkedHashMap<>();
        trips.forEach(trip -> grouped.computeIfAbsent(
                trip.getOrigin() + "\u0000" + trip.getDestination() + "\u0000" + trip.getProvider(),
                key -> new ArrayList<>()).add(trip));
        return grouped.values().stream()
                .map(routeTrips -> {
                    Trip first = routeTrips.getFirst();
                    long total = routeTrips.stream().mapToLong(Trip::getFareCents).sum();
                    return new SpendingHistoryResponse.RouteSlice(
                            first.getOrigin(), first.getDestination(), first.getProvider(),
                            routeTrips.size(), total, Math.round((double) total / routeTrips.size()));
                })
                .sorted(Comparator.comparingLong(SpendingHistoryResponse.RouteSlice::tripCount)
                        .reversed()
                        .thenComparing(SpendingHistoryResponse.RouteSlice::origin))
                .limit(5)
                .toList();
    }

    /**
     * Which range chips are worth offering. A rider two weeks old gets 7d and 30d
     * enabled and the longer ranges visibly unavailable, instead of two chips that
     * silently render nothing.
     */
    private List<String> rangesWithData(User user, ZoneId zone, LocalDate today) {
        Instant end = today.plusDays(1).atStartOfDay(zone).toInstant();
        List<String> withData = new ArrayList<>();
        for (HistoryRange candidate : HistoryRange.values()) {
            Instant start = today.minusDays(candidate.days() - 1L).atStartOfDay(zone).toInstant();
            if (tripRepository.countCompletedBetween(user.getId(), start, end) > 0) {
                withData.add(candidate.code());
            }
        }
        return withData;
    }

    private static String displayNameOf(String provider) {
        try {
            return TransitProvider.valueOf(provider).displayName();
        } catch (IllegalArgumentException exception) {
            return provider;
        }
    }

    private static String titleCase(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String lower = raw.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
