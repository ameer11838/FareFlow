package com.fareflow.journey;

import com.fareflow.location.LocationCandidate;

import java.util.ArrayList;
import java.util.List;

/** Builds an honest stop-boundary sequence on top of provider route geometry. */
public final class TransitStopGeometry {

    private TransitStopGeometry() {
    }

    /**
     * Returns the original geometry when it already contains a complete named
     * stop sequence. Otherwise it adds numbered markers at equal travelled-distance
     * intervals while preserving the provider's real boarding and alighting names.
     */
    public static List<JourneyLeg.Waypoint> ensureStopBoundaries(
            List<JourneyLeg.Waypoint> geometry, String startName, String endName,
            String lineName, Integer stopCount) {
        List<JourneyLeg.Waypoint> points = geometry == null ? List.of() : List.copyOf(geometry);
        if (points.isEmpty() || stopCount == null || stopCount <= 0) return points;
        long named = points.stream()
                .filter(point -> point.name() != null && !point.name().isBlank())
                .count();
        if (named >= stopCount + 1L) return points;

        if (points.size() == 1 || stopCount == 1) {
            List<JourneyLeg.Waypoint> labelled = new ArrayList<>(points);
            labelEndpoints(labelled, startName, endName);
            return List.copyOf(labelled);
        }

        double totalDistance = pathDistance(points);
        if (totalDistance <= 0) {
            List<JourneyLeg.Waypoint> labelled = new ArrayList<>(points);
            labelEndpoints(labelled, startName, endName);
            return List.copyOf(labelled);
        }

        List<JourneyLeg.Waypoint> labelled = new ArrayList<>();
        JourneyLeg.Waypoint first = points.getFirst();
        labelled.add(new JourneyLeg.Waypoint(startName, first.latitude(), first.longitude()));

        int boundary = 1;
        double traversed = 0;
        for (int index = 1; index < points.size(); index++) {
            JourneyLeg.Waypoint previous = points.get(index - 1);
            JourneyLeg.Waypoint current = points.get(index);
            double segment = LocationCandidate.haversineMetres(
                    previous.latitude(), previous.longitude(), current.latitude(), current.longitude());

            while (boundary < stopCount
                    && totalDistance * boundary / stopCount <= traversed + segment) {
                double target = totalDistance * boundary / stopCount;
                double ratio = segment <= 0 ? 0 : Math.clamp((target - traversed) / segment, 0, 1);
                JourneyLeg.Waypoint marker = new JourneyLeg.Waypoint(
                        "%s · stop %d of %d".formatted(lineName, boundary, stopCount),
                        previous.latitude() + (current.latitude() - previous.latitude()) * ratio,
                        previous.longitude() + (current.longitude() - previous.longitude()) * ratio);
                appendOrLabel(labelled, marker);
                boundary++;
            }
            appendDistinct(labelled, current);
            traversed += segment;
        }

        JourneyLeg.Waypoint last = labelled.getLast();
        labelled.set(labelled.size() - 1,
                new JourneyLeg.Waypoint(endName, last.latitude(), last.longitude()));
        return List.copyOf(labelled);
    }

    private static void labelEndpoints(List<JourneyLeg.Waypoint> points,
                                       String startName, String endName) {
        if (points.isEmpty()) return;
        JourneyLeg.Waypoint first = points.getFirst();
        points.set(0, new JourneyLeg.Waypoint(startName, first.latitude(), first.longitude()));
        JourneyLeg.Waypoint last = points.getLast();
        points.set(points.size() - 1,
                new JourneyLeg.Waypoint(endName, last.latitude(), last.longitude()));
    }

    private static void appendOrLabel(List<JourneyLeg.Waypoint> points,
                                      JourneyLeg.Waypoint marker) {
        if (!points.isEmpty() && sameCoordinate(points.getLast(), marker)) {
            points.set(points.size() - 1, marker);
        } else {
            points.add(marker);
        }
    }

    private static void appendDistinct(List<JourneyLeg.Waypoint> points,
                                       JourneyLeg.Waypoint point) {
        if (points.isEmpty() || !sameCoordinate(points.getLast(), point)) points.add(point);
    }

    private static boolean sameCoordinate(JourneyLeg.Waypoint left, JourneyLeg.Waypoint right) {
        return Double.compare(left.latitude(), right.latitude()) == 0
                && Double.compare(left.longitude(), right.longitude()) == 0;
    }

    private static double pathDistance(List<JourneyLeg.Waypoint> points) {
        double distance = 0;
        for (int index = 1; index < points.size(); index++) {
            JourneyLeg.Waypoint previous = points.get(index - 1);
            JourneyLeg.Waypoint current = points.get(index);
            distance += LocationCandidate.haversineMetres(
                    previous.latitude(), previous.longitude(), current.latitude(), current.longitude());
        }
        return distance;
    }
}
