package com.tripbudget.tripbudget_core.maps.clients;

import com.tripbudget.tripbudget_core.maps.config.MapsSettings;
import com.tripbudget.tripbudget_core.maps.dtos.response.MapCoordinatesResponse;
import com.tripbudget.tripbudget_core.maps.dtos.response.MapDistanceElementResponse;
import com.tripbudget.tripbudget_core.maps.dtos.response.MapDistanceRowResponse;
import com.tripbudget.tripbudget_core.maps.dtos.response.MapPlacePredictionResponse;
import com.tripbudget.tripbudget_core.maps.web.MapsException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

@Component
@RequiredArgsConstructor
public class VietmapClient {

    private static final String BASE_URL = "https://maps.vietmap.vn/api/";

    private final MapsHttpClient httpClient;
    private final MapsSettings settings;

    public boolean configured() {
        return !settings.getApiKey().isBlank();
    }

    public List<MapPlacePredictionResponse> search(String text, boolean regionsOnly) {
        Map<String, List<String>> query = createAuthenticatedQuery();
        query.put("text", List.of(text));
        query.put("display_type", List.of("1"));
        if (regionsOnly) {
            query.put("layers", List.of("CITY,DIST,WARD"));
        }
        JsonNode response = httpClient.get(BASE_URL + "autocomplete/v4", query, false);
        if (!response.isArray()) {
            throw MapsException.provider();
        }

        List<MapPlacePredictionResponse> predictions = new ArrayList<>();
        for (JsonNode place : response) {
            String referenceId = place.path("ref_id").asText("");
            String display = place.path("display").asText("");
            if (referenceId.isBlank() || display.isBlank()) {
                continue;
            }
            String name = place.path("name").asText("");
            String mainText = name.isBlank() ? display : name;
            predictions.add(new MapPlacePredictionResponse(
                    referenceId,
                    mainText,
                    place.path("address").asText(""),
                    display,
                    "vietmap",
                    null
            ));
        }
        return predictions;
    }

    public MapCoordinatesResponse geocode(String location) {
        Map<String, List<String>> query = createAuthenticatedQuery();
        query.put("text", List.of(location));
        query.put("display_type", List.of("1"));
        JsonNode searchResponse = httpClient.get(BASE_URL + "search/v4", query, false);
        if (!searchResponse.isArray()) {
            throw MapsException.provider();
        }

        // Search/Autocomplete trả mã địa điểm; Place mới trả tọa độ.
        String referenceId = "";
        if (!searchResponse.isEmpty()) {
            referenceId = searchResponse.get(0).path("ref_id").asText("");
        }
        if (referenceId.isBlank()) {
            List<MapPlacePredictionResponse> predictions = search(location, false);
            if (predictions.isEmpty()) {
                return null;
            }
            referenceId = predictions.getFirst().placeId();
        }

        Map<String, List<String>> detailQuery = createAuthenticatedQuery();
        detailQuery.put("refid", List.of(referenceId));
        JsonNode placeResponse = httpClient.get(BASE_URL + "place/v4", detailQuery, false);
        return MapResponseMapper.coordinates(placeResponse.path("lat"), placeResponse.path("lng"));
    }

    public List<MapDistanceRowResponse> matrix(List<MapCoordinatesResponse> origins,
            List<MapCoordinatesResponse> destinations, String language) {
        int batchSize = settings.getMaxPoints() / 2;
        List<MapDistanceRowResponse> result = createEmptyRows(origins.size(), destinations.size());
        for (int originOffset = 0; originOffset < origins.size(); originOffset += batchSize) {
            for (int destinationOffset = 0; destinationOffset < destinations.size(); destinationOffset += batchSize) {
                List<MapCoordinatesResponse> originBatch = origins.subList(
                        originOffset, Math.min(originOffset + batchSize, origins.size()));
                List<MapCoordinatesResponse> destinationBatch = destinations.subList(
                        destinationOffset, Math.min(destinationOffset + batchSize, destinations.size()));

                List<MapDistanceRowResponse> batchRows = requestMatrixBatch(originBatch, destinationBatch, language);
                for (int rowIndex = 0; rowIndex < originBatch.size(); rowIndex++) {
                    for (int columnIndex = 0; columnIndex < destinationBatch.size(); columnIndex++) {
                        MapDistanceElementResponse element = batchRows.get(rowIndex).elements().get(columnIndex);
                        result.get(originOffset + rowIndex).elements().set(destinationOffset + columnIndex, element);
                    }
                }
            }
        }
        return result;
    }

    private List<MapDistanceRowResponse> requestMatrixBatch(List<MapCoordinatesResponse> origins,
            List<MapCoordinatesResponse> destinations, String language) {
        List<String> points = new ArrayList<>();
        for (MapCoordinatesResponse point : origins) {
            points.add(point.latitude() + "," + point.longitude());
        }
        for (MapCoordinatesResponse point : destinations) {
            points.add(point.latitude() + "," + point.longitude());
        }

        Map<String, List<String>> query = createAuthenticatedQuery();
        query.put("point", points);
        query.put("sources", List.of(formatPointIndices(0, origins.size())));
        query.put("destinations", List.of(formatPointIndices(origins.size(), destinations.size())));
        query.put("vehicle", List.of("car"));
        query.put("points_encoded", List.of("false"));

        query.put("annotation", List.of("distance"));
        JsonNode distances = requestMatrix(query).path("distances");
        query.put("annotation", List.of("duration"));
        JsonNode durations = requestMatrix(query).path("durations");
        return MapResponseMapper.matrix(distances, durations, origins.size(), destinations.size(), "vietmap", language);
    }

    private List<MapDistanceRowResponse> createEmptyRows(int originCount, int destinationCount) {
        List<MapDistanceRowResponse> rows = new ArrayList<>();
        for (int originIndex = 0; originIndex < originCount; originIndex++) {
            List<MapDistanceElementResponse> elements = new ArrayList<>();
            for (int destinationIndex = 0; destinationIndex < destinationCount; destinationIndex++) {
                elements.add(new MapDistanceElementResponse("ZERO_RESULTS", "vietmap", null, null));
            }
            rows.add(new MapDistanceRowResponse(elements));
        }
        return rows;
    }

    private JsonNode requestMatrix(Map<String, List<String>> query) {
        JsonNode response = httpClient.get(BASE_URL + "matrix/v4", query, false);
        if (!"OK".equals(response.path("code").asText())) {
            throw MapsException.provider();
        }
        return response;
    }

    private Map<String, List<String>> createAuthenticatedQuery() {
        if (!configured()) {
            throw MapsException.provider();
        }
        Map<String, List<String>> query = new LinkedHashMap<>();
        query.put("apikey", List.of(settings.getApiKey()));
        return query;
    }

    private String formatPointIndices(int offset, int count) {
        StringJoiner indices = new StringJoiner(";");
        for (int index = offset; index < offset + count; index++) {
            indices.add(String.valueOf(index));
        }
        return indices.toString();
    }
}
