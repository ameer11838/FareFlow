package com.fareflow.fare;

import com.fareflow.common.Money;
import com.fareflow.fare.rules.FareCap;
import com.fareflow.fare.rules.FarePolicy;
import com.fareflow.fare.rules.TransferRule;
import com.fareflow.journey.Journey;
import com.fareflow.journey.JourneyLeg;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Prices a {@link Journey} under published tariffs, transfer agreements, and caps.
 *
 * <p>Ordering matters and is deliberate: base fares, then transfer credits, then
 * caps. A cap is a ceiling on what a rider actually pays, so it has to be applied
 * to the post-credit amount — capping first would let a transfer credit push the
 * total below the cap and quietly give away money twice.
 *
 * <p><strong>Unknown fares never become zero.</strong> If any leg cannot be priced,
 * the whole journey is {@code UNKNOWN} with no total. A journey that nobody can
 * price must not win on cost.
 *
 * <p>Plain Java: policies and rules arrive through the constructor, so the engine
 * is unit-testable with no Spring context and no database.
 */
public final class FareEngine {

    private final Map<String, FarePolicy> policiesByCode;
    private final List<TransferRule> transferRules;
    private final List<FareCap> fareCaps;

    public FareEngine(List<FarePolicy> policies, List<TransferRule> transferRules, List<FareCap> fareCaps) {
        this.policiesByCode = policies.stream()
                .collect(Collectors.toMap(FarePolicy::code, Function.identity()));
        this.transferRules = List.copyOf(transferRules);
        this.fareCaps = List.copyOf(fareCaps);
    }

    /**
     * @param policyByLineCode maps a leg's line to the policy that prices it
     */
    public FareCalculation price(Journey journey,
                                 UserFareContext context,
                                 Map<String, String> policyByLineCode) {

        List<FareLine> lines = new ArrayList<>();
        long baseTotal = 0;
        long transferTotal = 0;
        // Per-agency running totals. A cap belongs to one agency, so it must only
        // ever reduce that agency's share -- capping the journey total would let an
        // MTA cap discount a SEPTA ticket.
        java.util.Map<String, Long> chargeableByAgency = new java.util.LinkedHashMap<>();
        boolean anyUnpriced = false;
        boolean anyEstimated = false;
        String previousAgency = null;

        for (JourneyLeg leg : journey.legs()) {
            if (!leg.mode().isTransit()) {
                continue; // Walking is free.
            }

            FarePolicy policy = resolvePolicy(leg, policyByLineCode);
            if (policy == null) {
                anyUnpriced = true;
                lines.add(FareLine.unpriced("%s — no fare rule".formatted(leg.lineName())));
                previousAgency = leg.agency();
                continue;
            }

            Optional<Long> base = policy.baseFareCents(leg);
            if (base.isEmpty()) {
                anyUnpriced = true;
                lines.add(FareLine.unpriced(policy.describe(leg)));
                previousAgency = leg.agency();
                continue;
            }

            long legFare = base.get();

            // A valid pass makes this agency's leg free, and says so on the receipt.
            if (context.holdsPassFor(leg.agency())) {
                lines.add(FareLine.base(policy.describe(leg), legFare));
                lines.add(FareLine.passAdjustment("%s pass".formatted(leg.agency()), legFare));
                baseTotal += legFare;
                transferTotal -= legFare;
                previousAgency = leg.agency();
                continue;
            }

            lines.add(FareLine.base(policy.describe(leg), legFare));
            baseTotal += legFare;
            chargeableByAgency.merge(leg.agency(), legFare, Long::sum);

            if (policy instanceof com.fareflow.fare.rules.DistanceBandFarePolicy) {
                anyEstimated = true;
            }

            // Transfer credit, capped at this leg's fare: a credit may zero a fare
            // but must never turn it into a payout.
            if (previousAgency != null) {
                String from = previousAgency;
                Optional<TransferRule> rule = transferRules.stream()
                        .filter(candidate -> candidate.matches(from, leg.agency()))
                        .findFirst();
                if (rule.isPresent()) {
                    long credit = Math.min(rule.get().creditCents(), legFare);
                    if (credit > 0) {
                        lines.add(FareLine.transferCredit(rule.get().label(), credit));
                        transferTotal -= credit;
                        // The credit reduces the receiving agency's share too.
                        chargeableByAgency.merge(leg.agency(), -credit, Long::sum);
                    }
                }
            }

            previousAgency = leg.agency();
        }

        if (anyUnpriced) {
            // One unpriceable leg makes the journey total unknowable. Reporting a
            // partial total would understate the real cost.
            return FareCalculation.unknown(List.copyOf(lines),
                    "At least one leg has no published fare rule");
        }

        long afterTransfers = baseTotal + transferTotal;
        long capAdjustment = applyCaps(context, chargeableByAgency, lines);
        long total = Math.max(0, afterTransfers + capAdjustment);

        return new FareCalculation(
                total,
                baseTotal,
                transferTotal,
                capAdjustment,
                0,
                anyEstimated ? FareStatus.ESTIMATED : FareStatus.EXACT,
                FareSource.FARE_RULE_ENGINE,
                List.copyOf(lines));
    }

    /**
     * Applies each cap to its own agency's share of the fare only.
     *
     * <p>Scoping matters: an MTA weekly cap must not discount a SEPTA ticket that
     * happens to share an itinerary with a subway ride. Returns a negative
     * adjustment, or zero.
     */
    private long applyCaps(UserFareContext context,
                           java.util.Map<String, Long> chargeableByAgency,
                           List<FareLine> lines) {
        long adjustment = 0;

        for (FareCap cap : fareCaps) {
            long agencyShare = chargeableByAgency.getOrDefault(cap.agency(), 0L);
            if (agencyShare <= 0) {
                continue;
            }

            long alreadySpent = cap.period() == FareCap.Period.WEEKLY
                    ? context.spentThisWeekCents()
                    : context.spentTodayCents();

            long chargeable = cap.chargeableCents(alreadySpent, agencyShare);
            long reduction = agencyShare - chargeable;
            if (reduction > 0) {
                lines.add(FareLine.capAdjustment(
                        "%s (%s remaining)".formatted(cap.label(),
                                Money.format(Math.max(0, cap.capCents() - alreadySpent))),
                        reduction));
                adjustment -= reduction;
            }
        }
        return adjustment;
    }

    private FarePolicy resolvePolicy(JourneyLeg leg, Map<String, String> policyByLineCode) {
        String policyCode = policyByLineCode.get(leg.lineCode());
        return policyCode == null ? null : policiesByCode.get(policyCode);
    }
}
