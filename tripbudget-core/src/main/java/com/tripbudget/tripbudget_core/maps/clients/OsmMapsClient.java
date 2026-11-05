package com.tripbudget.tripbudget_core.maps.clients;

import com.tripbudget.tripbudget_core.maps.config.MapsSettings;
import com.tripbudget.tripbudget_core.maps.dtos.response.MapCoordinatesResponse;
import com.tripbudget.tripbudget_core.maps.dtos.response.MapDistanceRowResponse;
import com.tripbudget.tripbudget_core.maps.dtos.response.MapPlacePredictionResponse;
import com.tripbudget.tripbudget_core.maps.web.MapsException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

@Component
@RequiredArgsConstructor
public class OsmMapsClient {

    private static final int SEARCH_CACHE_MAX_ENTRIES = 256;
    private static final long SEARCH_CACHE_TTL_MILLIS = 300_000;

    private final MapsHttpClient httpClient;
    private final MapsSettings settings;
    private final Map<String, CachedSearch> searchCache = new LinkedHashMap<>();

    private record CachedSearch(long expiresAt, List<MapPlacePredictionResponse> predictions) {
    }

    public List<MapPlacePredictionResponse> search(String text, String language, boolean regionsOnly) {
        Map<String, List<String>> query = new LinkedHashMap<>();
        query.put("q", List.of(text));
        query.put("limit", List.of("8"));
        if ("en".equals(language)) {
            query.put("lang", List.of("en"));
        }
        if (regionsOnly) {
            query.put("layer", List.of("city", "state", "county", "district", "locality", "country"));
        }

        String cacheKey = query.toString();
        synchronized (searchCache) {
            CachedSearch cached = searchCache.get(cacheKey);
            if (cached != null && cached.expiresAt() > System.currentTimeMillis()) {
                return cached.predictions();
            }
        }
        JsonNode response = httpClient.get(settings.getPhotonUrl(), query, true);
        if (!response.path("features").isArray()) {
            throw MapsException.provider();
        }
        List<MapPlacePredictionResponse> predictions = new ArrayList<>();
        for (JsonNode feature : response.path("features")) {
            MapPlacePredictionResponse prediction = toPrediction(feature);
            if (prediction != null) {
                predictions.add(prediction);
            }
        }
        synchronized (searchCache) {
            if (searchCache.size() >= SEARCH_CACHE_MAX_ENTRIES) {
                String oldestKey = searchCache.keySet().iterator().next();
                searchCache.remove(oldestKey);
            }
            long expiresAt = System.currentTimeMillis() + SEARCH_CACHE_TTL_MILLIS;
            searchCache.put(cacheKey, new CachedSearch(expiresAt, List.copyOf(predictions)));
        }
        return predictions;
    }

    public List<MapDistanceRowResponse> matrix(List<MapCoordinatesResponse> origins,
            List<MapCoordinatesResponse> destinations, String language) {
        List<MapCoordinatesResponse> points = new ArrayList<>(origins);
        points.addAll(destinations);

        StringJoiner coordinates = new StringJoiner(";");
        for (MapCoordinatesResponse point : points) {
            coordinates.add(point.longitude() + "," + point.latitude());
        }
        Map<String, List<String>> query = Map.of(
                "sources", List.of(formatPointIndices(0, origins.size())),
                "destinations", List.of(formatPointIndices(origins.size(), destinations.size())),
                "annotations", List.of("distance,duration")
        );
        String url = settings.getRoutingUrl() + "/table/v1/driving/" + coordinates;
        JsonNode response = httpClient.get(url, query, true);
        if (!"Ok".equals(response.path("code").asText())) {
            throw MapsException.provider();
        }
        return MapResponseMapper.matrix(
                response.path("distances"), response.path("durations"),
                origins.size(), destinations.size(), "osm", language
        );
    }

    private MapPlacePredictionResponse toPrediction(JsonNode feature) {
        JsonNode coordinatesNode = feature.path("geometry").path("coordinates");
        if (!coordinatesNode.isArray() || coordinatesNode.size() < 2) {
            return null;
        }
        MapCoordinatesResponse coordinates = MapResponseMapper.coordinates(coordinatesNode.get(1), coordinatesNode.get(0));
        if (coordinates == null) {
            return null;
        }
        JsonNode properties = feature.path("properties");
        String name = readPlaceName(properties);
        if (name.isBlank()) {
            return null;
        }

        LinkedHashSet<String> addressParts = new LinkedHashSet<>();
        for (String field : List.of("street", "city", "state", "country")) {
            String value = properties.path(field).asText("");
            if (!value.isBlank() && !value.equals(name)) {
                addressParts.add(value);
            }
        }
        String secondaryText = String.join(", ", addressParts);
        String coordinateId = coordinates.latitude() + "," + coordinates.longitude();
        String placeId = "osm:" + properties.path("osm_type").asText("N")
                + ":" + properties.path("osm_id").asText(coordinateId);
        String description = secondaryText.isBlank() ? name : name + ", " + secondaryText;
        return new MapPlacePredictionResponse(placeId, name, secondaryText, description, "osm", coordinates);
    }

    private String readPlaceName(JsonNode properties) {
        String name = properties.path("name").asText("");
        if (name.isBlank()) {
            name = (properties.path("housenumber").asText("") + " " + properties.path("street").asText("")).trim();
        }
        if (name.isBlank()) {
            name = properties.path("city").asText(properties.path("state").asText(properties.path("country").asText("")));
        }
        return name;
    }

    private String formatPointIndices(int offset, int count) {
        StringJoiner indices = new StringJoiner(";");
        for (int index = offset; index < offset + count; index++) {
            indices.add(String.valueOf(index));
        }
        return indices.toString();
    }
}
