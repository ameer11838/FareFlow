package com.fareflow.session.dto;

/** Optional rider-confirmed exception; an omitted body means the stop was reached. */
public record AdvanceTransitSessionRequest(String outcome) {
}
