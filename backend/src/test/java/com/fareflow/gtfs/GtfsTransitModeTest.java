package com.fareflow.gtfs;

import com.fareflow.journey.TransitMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GtfsTransitModeTest {

    @Test
    void supportsOfficialFareFlowTransitModes() {
        assertThat(GtfsTransitMode.fromRouteType(0)).contains(TransitMode.LIGHT_RAIL);
        assertThat(GtfsTransitMode.fromRouteType(1)).contains(TransitMode.SUBWAY);
        assertThat(GtfsTransitMode.fromRouteType(2)).contains(TransitMode.RAIL);
        assertThat(GtfsTransitMode.fromRouteType(3)).contains(TransitMode.BUS);
        assertThat(GtfsTransitMode.fromRouteType(4)).contains(TransitMode.FERRY);
        assertThat(GtfsTransitMode.fromRouteType(11)).contains(TransitMode.BUS);
        assertThat(GtfsTransitMode.fromRouteType(12)).contains(TransitMode.RAIL);
    }

    @Test
    void mapsCommonExtendedTypesButRejectsOutOfScopeTransport() {
        assertThat(GtfsTransitMode.fromRouteType(200)).contains(TransitMode.BUS);
        assertThat(GtfsTransitMode.fromRouteType(401)).contains(TransitMode.SUBWAY);
        assertThat(GtfsTransitMode.fromRouteType(700)).contains(TransitMode.BUS);
        assertThat(GtfsTransitMode.fromRouteType(900)).contains(TransitMode.LIGHT_RAIL);
        assertThat(GtfsTransitMode.fromRouteType(1000)).contains(TransitMode.FERRY);
        assertThat(GtfsTransitMode.fromRouteType(1200)).contains(TransitMode.FERRY);
        assertThat(GtfsTransitMode.fromRouteType(1100)).isEmpty(); // air
        assertThat(GtfsTransitMode.fromRouteType(1500)).isEmpty(); // taxi
        assertThat(GtfsTransitMode.fromRouteType(1504)).isEmpty(); // bike taxi
    }
}
