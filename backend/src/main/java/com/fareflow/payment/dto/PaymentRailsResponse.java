package com.fareflow.payment.dto;

import java.util.List;

/**
 * Which payment rails this deployment can actually complete.
 *
 * <p>Asked before the rider chooses, not discovered when they press pay. An
 * unconfigured rail is absent from the list rather than present and broken —
 * the same contract the client already relies on for the map and the assistant.
 *
 * @param network the XRPL network RLUSD settles on, when that rail is available
 */
public record PaymentRailsResponse(List<String> rails, String network) {
}
