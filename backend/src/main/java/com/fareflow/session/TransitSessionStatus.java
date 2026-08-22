package com.fareflow.session;

public enum TransitSessionStatus {
    STARTED,
    IN_PROGRESS,
    COMPLETED,
    NO_CHARGE,
    PAID;

    public boolean isActive() {
        return this == STARTED || this == IN_PROGRESS;
    }
}
