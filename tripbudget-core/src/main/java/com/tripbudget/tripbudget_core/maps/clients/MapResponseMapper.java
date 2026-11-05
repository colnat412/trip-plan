package com.tripbudget.tripbudget_core.maps.clients;

import com.tripbudget.tripbudget_core.maps.dtos.response.MapCoordinatesResponse;
import com.tripbudget.tripbudget_core.maps.dtos.response.MapDistanceElementResponse;
import com.tripbudget.tripbudget_core.maps.dtos.response.MapDistanceRowResponse;
import com.tripbudget.tripbudget_core.maps.dtos.response.MapMeasurementResponse;
import com.tripbudget.tripbudget_core.maps.web.MapsException;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class MapResponseMapper {

    private MapResponseMapper() {
    }

    static MapCoordinatesResponse coordinates(JsonNode latitude, JsonNode longitude) {
        if (!latitude.isNumber() || !longitude.isNumber()) {
            return null;
        }
        double latitudeValue = latitude.asDouble();
        double longitudeValue = longitude.asDouble();
        if (!Double.isFinite(latitudeValue) || !Double.isFinite(longitudeValue)
                || Math.abs(latitudeValue) > 90 || Math.abs(longitudeValue) > 180) {
            return null;
        }
        return new MapCoordinatesResponse(latitudeValue, longitudeValue);
    }

    static List<MapDistanceRowResponse> matrix(JsonNode distances, JsonNode durations,
            int originCount, int destinationCount, String source, String language) {
        if (!distances.isArray() || !durations.isArray()
                || distances.size() != originCount || durations.size() != originCount) {
            throw MapsException.provider();
        }

        List<MapDistanceRowResponse> rows = new ArrayList<>();
        for (int originIndex = 0; originIndex < originCount; originIndex++) {
            JsonNode distanceRow = distances.get(originIndex);
            JsonNode durationRow = durations.get(originIndex);
            if (!distanceRow.isArray() || !durationRow.isArray()
                    || distanceRow.size() != destinationCount || durationRow.size() != destinationCount) {
                throw MapsException.provider();
            }

            List<MapDistanceElementResponse> elements = new ArrayList<>();
            for (int destinationIndex = 0; destinationIndex < destinationCount; destinationIndex++) {
                JsonNode distance = distanceRow.get(destinationIndex);
                JsonNode duration = durationRow.get(destinationIndex);
                elements.add(toDistanceElement(distance, duration, source, language));
            }
            rows.add(new MapDistanceRowResponse(elements));
        }
        return rows;
    }

    private static MapDistanceElementResponse toDistanceElement(JsonNode distance, JsonNode duration,
            String source, String language) {
        if (!distance.isNumber() || !duration.isNumber()
                || !Double.isFinite(distance.asDouble()) || !Double.isFinite(duration.asDouble())
                || distance.asDouble() < 0 || duration.asDouble() < 0) {
            return new MapDistanceElementResponse("ZERO_RESULTS", source, null, null);
        }
        double meters = distance.asDouble();
        double seconds = duration.asDouble();
        String distanceText = meters < 1000
                ? Math.round(meters) + " m"
                : String.format(Locale.ROOT, "%.1f km", meters / 1000);
        String durationText = (long) Math.ceil(seconds / 60) + ("en".equals(language) ? " min" : " phút");
        return new MapDistanceElementResponse(
                "OK",
                source,
                new MapMeasurementResponse(meters, distanceText),
                new MapMeasurementResponse(seconds, durationText)
        );
    }
}
