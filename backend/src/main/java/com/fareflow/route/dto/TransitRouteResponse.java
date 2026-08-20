package com.fareflow.route.dto;

import com.fareflow.route.TransitRoute;

public record TransitRouteResponse(
        long id,
        String origin,
        String destination,
        String provider,
        String providerName,
        String mode,
        int durationMinutes,
        long fareCents,
        int transfers
) {

    public static TransitRouteResponse from(TransitRoute route) {
        return new TransitRouteResponse(
                route.getId(),
                route.getOrigin(),
                route.getDestination(),
                route.getProvider().name(),
                route.getProvider().displayName(),
                route.getMode().name(),
                route.getDurationMinutes(),
                route.getFareCents(),
                route.getTransfers());
    }
}
