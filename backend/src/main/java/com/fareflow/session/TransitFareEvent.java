package com.fareflow.session;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** Immutable audit event for one stop boundary and every adjustment applied there. */
@Entity
@Table(name = "transit_fare_events")
public class TransitFareEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transit_session_id", nullable = false, updatable = false)
    private UUID transitSessionId;
    @Column(nullable = false, updatable = false)
    private int sequence;
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false)
    private TransitFareEventType eventType;
    @Column(name = "stop_name", updatable = false)
    private String stopName;
    @Column(name = "line_name", nullable = false, updatable = false)
    private String lineName;
    @Column(nullable = false, updatable = false)
    private String mode;
    @Column(updatable = false)
    private String agency;
    @Column(name = "gross_cents", nullable = false, updatable = false)
    private long grossCents;
    @Column(name = "transfer_discount_cents", nullable = false, updatable = false)
    private long transferDiscountCents;
    @Column(name = "concession_discount_cents", nullable = false, updatable = false)
    private long concessionDiscountCents;
    @Column(name = "cap_discount_cents", nullable = false, updatable = false)
    private long capDiscountCents;
    @Column(name = "amount_cents", nullable = false, updatable = false)
    private long amountCents;
    @Column(name = "cumulative_fare_cents", nullable = false, updatable = false)
    private long cumulativeFareCents;
    @Column(nullable = false, updatable = false)
    private String description;
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, updatable = false)
    private StopVerificationStatus verificationStatus;
    @Column(name = "verification_distance_metres", updatable = false)
    private Double verificationDistanceMetres;
    @Column(name = "reported_accuracy_metres", updatable = false)
    private Double reportedAccuracyMetres;
    @Column(name = "reported_latitude", updatable = false)
    private Double reportedLatitude;
    @Column(name = "reported_longitude", updatable = false)
    private Double reportedLongitude;
    @Column(name = "verification_note", updatable = false)
    private String verificationNote;

    protected TransitFareEvent() {}

    public static TransitFareEvent from(UUID sessionId, UsageFareEngine.StopFarePoint point,
                                        TransitProgressOutcome outcome, Instant occurredAt,
                                        StopVerification verification) {
        TransitFareEvent event = new TransitFareEvent();
        event.transitSessionId = sessionId;
        event.sequence = point.sequence();
        event.eventType = switch (outcome) {
            case REACHED -> TransitFareEventType.STOP_COMPLETED;
            case SKIPPED -> TransitFareEventType.STOP_SKIPPED;
            case DIVERTED -> TransitFareEventType.ROUTE_DIVERSION;
        };
        event.stopName = point.stopName();
        event.lineName = point.lineName();
        event.mode = point.mode();
        event.agency = point.agency();
        event.grossCents = point.grossCents();
        event.transferDiscountCents = point.transferDiscountCents();
        event.concessionDiscountCents = point.concessionDiscountCents();
        event.capDiscountCents = point.capDiscountCents();
        event.amountCents = point.fareIncrementCents();
        event.cumulativeFareCents = point.cumulativeFareCents();
        event.description = point.description();
        event.occurredAt = occurredAt;
        event.verificationStatus = verification.status();
        event.verificationDistanceMetres = verification.distanceMetres();
        event.reportedAccuracyMetres = verification.accuracyMetres();
        event.verificationNote = verification.explanation();
        // Only a contradicted stop keeps the raw position: that is the one case a
        // rider may need to dispute. Everywhere else the distance is the whole of
        // what the ledger reasons about, and the coordinates would be a
        // surveillance record the product has no use for.
        if (verification.status().isContradicted()) {
            event.reportedLatitude = verification.reportedLatitude();
            event.reportedLongitude = verification.reportedLongitude();
        }
        return event;
    }

    public Long getId() { return id; }
    public UUID getTransitSessionId() { return transitSessionId; }
    public int getSequence() { return sequence; }
    public TransitFareEventType getEventType() { return eventType; }
    public String getStopName() { return stopName; }
    public String getLineName() { return lineName; }
    public String getMode() { return mode; }
    public String getAgency() { return agency; }
    public long getGrossCents() { return grossCents; }
    public long getTransferDiscountCents() { return transferDiscountCents; }
    public long getConcessionDiscountCents() { return concessionDiscountCents; }
    public long getCapDiscountCents() { return capDiscountCents; }
    public long getAmountCents() { return amountCents; }
    public long getCumulativeFareCents() { return cumulativeFareCents; }
    public String getDescription() { return description; }
    public Instant getOccurredAt() { return occurredAt; }
    public StopVerificationStatus getVerificationStatus() { return verificationStatus; }
    public Double getVerificationDistanceMetres() { return verificationDistanceMetres; }
    public Double getReportedAccuracyMetres() { return reportedAccuracyMetres; }
    public Double getReportedLatitude() { return reportedLatitude; }
    public Double getReportedLongitude() { return reportedLongitude; }
    public String getVerificationNote() { return verificationNote; }
}
