package com.fareflow.profile;

import com.fareflow.location.LocationCandidate;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * A saved place on a rider's profile: a name that has been resolved to a point.
 *
 * <p>Storing "Newark" alone would push the ambiguity forward to every future
 * request — Newark, NJ and Newark, DE are both real, and a geocoder can change its
 * mind between sessions. Keeping the coordinates (and the geocoder's own id) means
 * the commute a rider saved on Monday is the same commute on Friday.
 *
 * <p>All-or-nothing: either every field is present or the place is absent. The
 * database enforces the same rule, so a half-resolved place cannot exist in either
 * layer.
 */
@Embeddable
public class TypicalPlace {

    @Column(name = "name")
    private String name;

    @Column(name = "lat")
    private Double latitude;

    @Column(name = "lon")
    private Double longitude;

    /** The geocoder's identifier, so the place can be re-resolved without a search. */
    @Column(name = "place_id")
    private String providerPlaceId;

    protected TypicalPlace() {
        // required by JPA
    }

    private TypicalPlace(String name, Double latitude, Double longitude, String providerPlaceId) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.providerPlaceId = providerPlaceId;
    }

    /** An absent place. Every field null, which is what the schema requires. */
    public static TypicalPlace empty() {
        return new TypicalPlace(null, null, null, null);
    }

    public static TypicalPlace of(String name, Double latitude, Double longitude, String providerPlaceId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A saved place needs a name");
        }
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException(
                    "A saved place needs coordinates. Pick a suggestion from the search box so "
                    + "FareFlow can resolve '%s' to a real location.".formatted(name.trim()));
        }
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("latitude out of range: " + latitude);
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("longitude out of range: " + longitude);
        }
        return new TypicalPlace(name.trim(), latitude, longitude,
                providerPlaceId == null || providerPlaceId.isBlank() ? null : providerPlaceId.trim());
    }

    public static TypicalPlace from(LocationCandidate candidate) {
        return of(candidate.displayName(), candidate.latitude(), candidate.longitude(),
                candidate.providerPlaceId());
    }

    public boolean isPresent() {
        return name != null && latitude != null && longitude != null;
    }

    public String name() {
        return name;
    }

    public Double latitude() {
        return latitude;
    }

    public Double longitude() {
        return longitude;
    }

    public String providerPlaceId() {
        return providerPlaceId;
    }
}
