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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        return search(text, regionsOnly, null);
    }

    public List<MapPlacePredictionResponse> search(String text, boolean regionsOnly, MapCoordinatesResponse center) {
        Map<String, List<String>> query = createAuthenticatedQuery();
        query.put("text", List.of(text));
        query.put("display_type", List.of("1"));
        if (regionsOnly) {
            query.put("layers", List.of("CITY,DIST,WARD"));
        }
        if (center != null) {
            String point = center.latitude() + "," + center.longitude();
            query.put("focus", List.of(point));
            query.put("circle_center", List.of(point));
            query.put("circle_radius", List.of("30000"));
        }
        JsonNode response = httpClient.get(BASE_URL + "autocomplete/v4", query);
        if (!response.isArray()) {
            throw MapsException.provider();
        }

        List<MapPlacePredictionResponse> predictions = new ArrayList<>();
        for (JsonNode place : response) {
            if (center != null && !isNearby(place, center)) continue;
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

    public MapCoordinatesResponse destinationCenter(String destination) {
        return geocode(destination);
    }

    public MapCoordinatesResponse geocode(String destination) {
        String[] parts = destination.split(",");
        String name = parts[0].trim();
        List<String> contextParts = new ArrayList<>();
        for (int index = 1; index < parts.length; index++) {
            String part = parts[index].trim();
            if (!Set.of("viet nam", "vietnam", "vn").contains(DiscoveryCategory.normalize(part))) {
                contextParts.add(part);
            }
        }
        String context = String.join(", ", contextParts);
        String locality = contextParts.isEmpty() ? "" : locationName(contextParts.getLast());
        MapPlacePredictionResponse match = matchingDestination(name, context, locality, true);
        // Provider records can omit a generic feature descriptor. Match the remaining
        // identity and locality for any destination type, never an unrelated first result.
        String identity = geographicIdentity(name);
        if (match == null && !identity.equals(DiscoveryCategory.normalize(name)) && identity.length() >= 3) {
            match = matchingDestination(identity, context, locality, false);
        }
        return match == null ? null : placeCoordinates(match.placeId());
    }

    private String geographicIdentity(String name) {
        return DiscoveryCategory.normalize(name).replaceFirst(
                "^(quang truong|bai bien|khu du lich|khu di tich|cong vien|ho nuoc|nui|thac|square|beach|park|lake|mountain)\\s+", "");
    }

    private MapPlacePredictionResponse matchingDestination(String name, String context, String locality, boolean preferExact) {
        String expected = locationName(name);
        MapPlacePredictionResponse best = null;
        int bestScore = 0;
        for (MapPlacePredictionResponse place : search(context.isBlank() ? name : name + ", " + context, false)) {
            if (!locality.isBlank() && !DiscoveryCategory.normalize(place.description()).contains(locality)) continue;
            String actual = locationName(place.mainText());
            String display = locationName(place.description().split(",")[0]);
            // Saved addresses may contain the venue and road in one comma-delimited part.
            int score = actual.equals(expected) ? (preferExact ? 4 : 3) : actual.startsWith(expected + " ") ? 3
                    : display.equals(expected) ? 2 : (" " + display + " ").contains(" " + expected + " ") ? 1 : 0;
            if (score > bestScore) {
                best = place;
                bestScore = score;
            }
        }
        return best;
    }

    private String locationName(String value) {
        String name = DiscoveryCategory.normalize(value)
                .replaceFirst("^(thanh pho|tp|tinh)\\s+", "")
                .replaceFirst("\\s+city$", "");
        return Set.of("tphcm", "tp hcm", "hcm", "saigon", "sai gon").contains(name)
                ? "ho chi minh" : switch (name) {
                    case "hanoi" -> "ha noi";
                    case "danang" -> "da nang";
                    case "dalat" -> "da lat";
                    default -> name;
                };
    }

    private MapCoordinatesResponse placeCoordinates(String referenceId) {
        Map<String, List<String>> detailQuery = createAuthenticatedQuery();
        detailQuery.put("refid", List.of(referenceId));
        JsonNode placeResponse = httpClient.get(BASE_URL + "place/v4", detailQuery);
        return MapResponseMapper.coordinates(placeResponse.path("lat"), placeResponse.path("lng"));
    }

    public List<MapPlacePredictionResponse> discover(String destination, String input,
            DiscoveryCategory category, MapCoordinatesResponse center) {
        List<List<MapPlacePredictionResponse>> groups = new ArrayList<>();
        List<DiscoveryCategory.Topic> topics = category.topics();
        if (category == DiscoveryCategory.OTHER) {
            if (input.isBlank()) return List.of();
            topics = List.of(new DiscoveryCategory.Topic("", ""));
        }
        for (DiscoveryCategory.Topic topic : topics) {
            List<MapPlacePredictionResponse> group = new ArrayList<>();
            Set<String> seenGroupPlaces = new HashSet<>();
            List<String> queries = discoveryQueries(destination, input, category, topic);
            for (String text : queries) {
                // Broaden the query only when needed, retaining the same category and area filters.
                if (group.size() >= 6) break;
                for (MapPlacePredictionResponse place : searchDiscoveryGroup(text, input, category, topic, center)) {
                    if (seenGroupPlaces.add(discoveryIdentity(place))) group.add(place);
                }
            }
            groups.add(group);
        }
        // Interleave subtypes instead of filling the list with one kind of business.
        List<MapPlacePredictionResponse> results = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        Set<String> seenPlaces = new HashSet<>();
        int longestGroup = groups.stream().mapToInt(List::size).max().orElse(0);
        for (int index = 0; index < longestGroup && results.size() < 40; index++) {
            for (List<MapPlacePredictionResponse> group : groups) {
                if (index >= group.size()) continue;
                MapPlacePredictionResponse place = group.get(index);
                String identity = discoveryIdentity(place);
                if (seenIds.contains(place.placeId()) || seenPlaces.contains(identity)) continue;
                seenIds.add(place.placeId());
                seenPlaces.add(identity);
                results.add(place);
                if (results.size() == 40) break;
            }
        }
        return results;
    }

    private List<String> discoveryQueries(String destination, String input,
            DiscoveryCategory category, DiscoveryCategory.Topic topic) {
        String primary = input.isBlank() ? topic.defaultQuery() : input;
        if (primary.isBlank()) primary = destination;
        if (category == DiscoveryCategory.OTHER) return List.of(primary);
        String secondary = destination;
        if (!input.isBlank()) {
            List<String> tokens = category.searchTokens(input);
            secondary = tokens.isEmpty() ? destination : String.join(" ", tokens);
        }
        return DiscoveryCategory.normalize(primary).equals(DiscoveryCategory.normalize(secondary))
                ? List.of(primary) : List.of(primary, secondary);
    }

    private List<MapPlacePredictionResponse> searchDiscoveryGroup(String text, String input,
            DiscoveryCategory category, DiscoveryCategory.Topic topic, MapCoordinatesResponse center) {
        Map<String, List<String>> query = createAuthenticatedQuery();
        query.put("text", List.of(text));
        query.put("layers", List.of("POI"));
        query.put("display_type", List.of("1"));
        String focus = center.latitude() + "," + center.longitude();
        query.put("focus", List.of(focus));
        query.put("circle_center", List.of(focus));
        query.put("circle_radius", List.of("30000"));
        if (!topic.code().isBlank()) query.put("cats", List.of(topic.code()));
        JsonNode response = httpClient.get(BASE_URL + "autocomplete/v4", query);
        if (!response.isArray()) throw MapsException.provider();
        List<MapPlacePredictionResponse> results = new ArrayList<>();
        List<String> tokens = category.searchTokens(input);
        for (JsonNode place : response) {
            if (!category.accepts(place, topic)) continue;
            if (!input.isBlank() && !matchesDiscoveryInput(place.path("name").asText(""), tokens)) continue;
            if (!isNearby(place, center)) continue;
            String referenceId = place.path("ref_id").asText("");
            String display = place.path("display").asText("");
            if (referenceId.isBlank() || display.isBlank()) continue;
            results.add(new MapPlacePredictionResponse(referenceId, place.path("name").asText(),
                    place.path("address").asText(""), display, "vietmap", null));
        }
        return results;
    }

    private boolean isNearby(JsonNode place, MapCoordinatesResponse center) {
        MapCoordinatesResponse point = MapResponseMapper.coordinates(place.path("lat"), place.path("lng"));
        if (point == null) {
            // Autocomplete v4 normally supplies distance (km) from the requested focus,
            // not coordinates. Require that measurement instead of guessing by city name.
            JsonNode distance = place.path("distance");
            return distance.isNumber() && Double.isFinite(distance.asDouble())
                    && distance.asDouble() >= 0 && distance.asDouble() <= 30;
        }
        double latitude = Math.toRadians(point.latitude() - center.latitude());
        double longitude = Math.toRadians(point.longitude() - center.longitude());
        double haversine = Math.pow(Math.sin(latitude / 2), 2)
                + Math.cos(Math.toRadians(center.latitude())) * Math.cos(Math.toRadians(point.latitude()))
                * Math.pow(Math.sin(longitude / 2), 2);
        double kilometers = 6371 * 2 * Math.asin(Math.sqrt(Math.min(1, haversine)));
        return kilometers <= 30;
    }

    private String discoveryIdentity(MapPlacePredictionResponse place) {
        return DiscoveryCategory.normalize(place.mainText()) + "|" + DiscoveryCategory.normalize(place.secondaryText());
    }

    private boolean matchesDiscoveryInput(String name, List<String> tokens) {
        String normalizedName = " " + DiscoveryCategory.normalize(name);
        for (String token : tokens) {
            if (!normalizedName.contains(" " + token)) return false;
        }
        return true;
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
        JsonNode response = httpClient.get(BASE_URL + "matrix/v4", query);
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
