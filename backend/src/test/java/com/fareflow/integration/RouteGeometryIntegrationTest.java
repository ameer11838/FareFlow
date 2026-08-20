package com.fareflow.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Geometry is served alongside recommendations but is a separate concern: the
 * scorer never sees it, and clients are told how much to trust it.
 */
class RouteGeometryIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("PATH carries its published station coordinates")
    void pathHasWaypoints() throws Exception {
        mockMvc.perform(get("/api/recommendations")
                        .param("origin", "Newark").param("destination", "Manhattan"))
                .andExpect(status().isOk())
                // Best value first, so options[0] is PATH under BALANCED.
                .andExpect(jsonPath("$.options[0].provider", is("PATH")))
                .andExpect(jsonPath("$.options[0].geometry.waypoints", hasSize(6)))
                .andExpect(jsonPath("$.options[0].geometry.waypoints[0].name", is("Newark Penn Station")))
                .andExpect(jsonPath("$.options[0].geometry.waypoints[5].name", is("World Trade Center")));
    }

    @Test
    @DisplayName("geometry is labelled SCHEMATIC, never implied to be surveyed")
    void geometryIsLabelled() throws Exception {
        // TomTom has no transit routing, so these are straight lines between real
        // stops. Saying so is the difference between honest and misleading.
        mockMvc.perform(get("/api/recommendations")
                        .param("origin", "Newark").param("destination", "Manhattan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options[0].geometry.source", is("SCHEMATIC")))
                .andExpect(jsonPath("$.options[1].geometry.source", is("SCHEMATIC")))
                .andExpect(jsonPath("$.options[2].geometry.source", is("SCHEMATIC")));
    }

    @Test
    @DisplayName("coordinates are real published station locations")
    void coordinatesAreReal() throws Exception {
        mockMvc.perform(get("/api/recommendations")
                        .param("origin", "Newark").param("destination", "Manhattan"))
                .andExpect(status().isOk())
                // Newark Penn Station. Coordinates are doubles in the DTO, so Jackson
                // emits a Double here -- unlike scores, which arrive as BigDecimal.
                .andExpect(jsonPath("$.options[0].geometry.waypoints[0].latitude",
                        is(closeTo(40.7357, 0.001))))
                .andExpect(jsonPath("$.options[0].geometry.waypoints[0].longitude",
                        is(closeTo(-74.1643, 0.001))));
    }

    @Test
    @DisplayName("every seeded route has geometry")
    void allRoutesHaveGeometry() throws Exception {
        mockMvc.perform(get("/api/recommendations")
                        .param("origin", "Hoboken").param("destination", "Manhattan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options", hasSize(3)))
                .andExpect(jsonPath("$.options[0].geometry.waypoints", hasSize(org.hamcrest.Matchers.greaterThan(1))))
                .andExpect(jsonPath("$.options[1].geometry.waypoints", hasSize(org.hamcrest.Matchers.greaterThan(1))))
                .andExpect(jsonPath("$.options[2].geometry.waypoints", hasSize(org.hamcrest.Matchers.greaterThan(1))));
    }

    @Test
    @DisplayName("adding geometry did not change any recommendation")
    void geometryDoesNotAffectScoring() throws Exception {
        // The regression that matters: shape must not leak into the engine.
        mockMvc.perform(get("/api/recommendations")
                        .param("origin", "Newark").param("destination", "Manhattan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options[0].provider", is("PATH")))
                .andExpect(jsonPath("$.options[0].score",
                        is(closeTo(new java.math.BigDecimal("0.2316"), new java.math.BigDecimal("0.0001")))));

        mockMvc.perform(get("/api/recommendations")
                        .param("origin", "Newark").param("destination", "Manhattan")
                        .param("profile", "RUSH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options[0].provider", is("NJ_TRANSIT")));
    }
}
