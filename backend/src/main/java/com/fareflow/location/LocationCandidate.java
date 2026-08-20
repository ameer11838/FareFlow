package com.fareflow.location;

/**
 * A resolved place: what the user typed, turned into coordinates.
 *
 * <p>Free text is not a routing identity. Everything downstream — stop matching,
 * journey planning, map bounds — works from the coordinates, so "Philadelphia",
 * "Philly", and "30th Street Station" resolve to different, precise places rather
 * than three strings that happen to look similar.
 *
 * @param providerPlaceId identifier from the geocoder, kept so a candidate can be
 *                        re-resolved later without another text search
 * @param source          which geocoder produced this, for traceability
 */
public record LocationCandidate(
        String providerPlaceId,
        String displayName,
        String locality,
        String region,
        String country,
        double latitude,
        double longitude,
        String type,
        String source
) {

    public LocationCandidate {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName is required");
        }
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("latitude out of range: " + latitude);
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("longitude out of range: " + longitude);
        }
    }

    /** Great-circle distance in metres. Haversine — accurate enough at city scale. */
    public double distanceMetresTo(double otherLatitude, double otherLongitude) {
        return haversineMetres(latitude, longitude, otherLatitude, otherLongitude);
    }

    public static double haversineMetres(double lat1, double lon1, double lat2, double lon2) {
        final double earthRadiusMetres = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * earthRadiusMetres * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
