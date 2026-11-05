package com.tripbudget.tripbudget_core.maps.services;

import com.tripbudget.tripbudget_core.maps.clients.OsmMapsClient;
import com.tripbudget.tripbudget_core.maps.clients.VietmapClient;
import com.tripbudget.tripbudget_core.maps.dtos.request.MapDistanceRequest;
import com.tripbudget.tripbudget_core.maps.dtos.response.MapCoordinatesResponse;
import com.tripbudget.tripbudget_core.maps.dtos.response.MapDistanceElementResponse;
import com.tripbudget.tripbudget_core.maps.dtos.response.MapDistanceMatrixResponse;
import com.tripbudget.tripbudget_core.maps.dtos.response.MapDistanceRowResponse;
import com.tripbudget.tripbudget_core.maps.dtos.response.MapLocationResponse;
import com.tripbudget.tripbudget_core.maps.dtos.response.MapLocationResultResponse;
import com.tripbudget.tripbudget_core.maps.dtos.response.MapPlacePredictionResponse;
import com.tripbudget.tripbudget_core.maps.dtos.response.MapSearchResponse;
import com.tripbudget.tripbudget_core.maps.web.MapsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MapsService {

    private static final Pattern COORDINATES = Pattern.compile("^(-?\\d+(?:\\.\\d+)?),\\s*(-?\\d+(?:\\.\\d+)?)$");
    private static final long VIETMAP_RETRY_DELAY_MILLIS = 30_000;

    private final VietmapClient vietmapClient;
    private final OsmMapsClient osmMapsClient;
    private final MapsBudget mapsBudget;
    private final Clock clock;
    private volatile long retryVietmapAt;

    @Autowired
    public MapsService(VietmapClient vietmapClient, OsmMapsClient osmMapsClient, MapsBudget mapsBudget) {
        this(vietmapClient, osmMapsClient, mapsBudget, Clock.systemUTC());
    }

    MapsService(VietmapClient vietmapClient, OsmMapsClient osmMapsClient, MapsBudget mapsBudget, Clock clock) {
        this.vietmapClient = vietmapClient;
        this.osmMapsClient = osmMapsClient;
        this.mapsBudget = mapsBudget;
        this.clock = clock;
    }

    public MapSearchResponse search(Long currentUserId, String input, String language, boolean regionsOnly) {
        requireUser(currentUserId);
        String query = validateText(input, 250);
        if (query.length() < 2) {
            return new MapSearchResponse(List.of(), null);
        }
        mapsBudget.consume(currentUserId, 0);
        String searchLanguage = normalizeLanguage(language);

        return withFallback(
                () -> searchVietmap(query, regionsOnly),
                () -> new MapSearchResponse(osmMapsClient.search(query, searchLanguage, regionsOnly), "osm")
        );
    }

    public MapLocationResultResponse location(Long currentUserId, String input, String language) {
        requireUser(currentUserId);
        String query = validateText(input, 500);
        if (query.isBlank()) {
            throw MapsException.invalid("Provide a location.");
        }
        MapCoordinatesResponse coordinates = parseCoordinates(query);
        mapsBudget.consume(currentUserId, 0);
        if (coordinates != null) {
            return new MapLocationResultResponse(new MapLocationResponse(coordinates, "osm"));
        }

        MapLocationResponse location = withFallback(
                () -> findLocation(query, normalizeLanguage(language), "vietmap"),
                () -> findLocation(query, normalizeLanguage(language), "osm")
        );
        return new MapLocationResultResponse(location);
    }

    public MapDistanceMatrixResponse distance(Long currentUserId, MapDistanceRequest request) {
        requireUser(currentUserId);
        if (request == null) {
            throw MapsException.invalid("Invalid request body.");
        }
        List<String> origins = readLocations(request.origins());
        List<String> destinations = readLocations(request.destinations());
        JsonNode contextNode = request.destinationContext();
        if (contextNode != null && !contextNode.isTextual()) {
            throw MapsException.invalid("Destination context must be text.");
        }
        String destinationContext = validateText(contextNode == null ? "" : contextNode.asText(), 500);
        String language = normalizeLanguage(request.language() == null ? "vi" : request.language().asText());

        for (String origin : origins) {
            parseCoordinates(origin);
        }
        for (String destination : destinations) {
            parseCoordinates(destination);
        }
        mapsBudget.consume(currentUserId, origins.size() * destinations.size());

        return withFallback(
                () -> calculateMatrix(origins, destinations, language, destinationContext, "vietmap"),
                () -> calculateMatrix(origins, destinations, language, destinationContext, "osm")
        );
    }

    private MapSearchResponse searchVietmap(String query, boolean regionsOnly) {
        List<MapPlacePredictionResponse> predictions = vietmapClient.search(query, regionsOnly);
        if (predictions.isEmpty()) {
            return null;
        }
        return new MapSearchResponse(predictions, "vietmap");
    }

    private MapLocationResponse findLocation(String address, String language, String source) {
        MapCoordinatesResponse coordinates;
        if ("vietmap".equals(source)) {
            coordinates = vietmapClient.geocode(address);
        } else {
            List<MapPlacePredictionResponse> predictions = osmMapsClient.search(address, language, false);
            coordinates = predictions.isEmpty() ? null : predictions.getFirst().coordinates();
        }
        if (coordinates == null) {
            return null;
        }
        return new MapLocationResponse(coordinates, source);
    }

    private MapDistanceMatrixResponse calculateMatrix(List<String> origins, List<String> destinations,
            String language, String destinationContext, String source) {
        Map<String, MapCoordinatesResponse> resolvedCoordinates = new LinkedHashMap<>();
        List<String> allLocations = new ArrayList<>(origins);
        allLocations.addAll(destinations);
        for (String location : allLocations) {
            if (resolvedCoordinates.containsKey(location)) {
                continue;
            }
            checkInterrupted();
            MapCoordinatesResponse coordinates = parseCoordinates(location);
            if (coordinates == null) {
                String address = appendDestinationContext(location, destinationContext);
                MapLocationResponse resolvedLocation = findLocation(address, language, source);
                coordinates = resolvedLocation == null ? null : resolvedLocation.coordinates();
            }
            if (coordinates == null && "vietmap".equals(source)) {
                return null;
            }
            resolvedCoordinates.put(location, coordinates);
        }

        List<Integer> originIndices = findResolvedIndices(origins, resolvedCoordinates);
        List<Integer> destinationIndices = findResolvedIndices(destinations, resolvedCoordinates);
        List<MapDistanceRowResponse> rows = createEmptyRows(origins.size(), destinations.size(), source);
        if (!originIndices.isEmpty() && !destinationIndices.isEmpty()) {
            List<MapCoordinatesResponse> originPoints = new ArrayList<>();
            for (int originIndex : originIndices) {
                originPoints.add(resolvedCoordinates.get(origins.get(originIndex)));
            }
            List<MapCoordinatesResponse> destinationPoints = new ArrayList<>();
            for (int destinationIndex : destinationIndices) {
                destinationPoints.add(resolvedCoordinates.get(destinations.get(destinationIndex)));
            }

            List<MapDistanceRowResponse> providerRows;
            if ("vietmap".equals(source)) {
                providerRows = vietmapClient.matrix(originPoints, destinationPoints, language);
            } else {
                providerRows = osmMapsClient.matrix(originPoints, destinationPoints, language);
            }

            for (int rowIndex = 0; rowIndex < originIndices.size(); rowIndex++) {
                int originalRowIndex = originIndices.get(rowIndex);
                for (int columnIndex = 0; columnIndex < destinationIndices.size(); columnIndex++) {
                    int originalColumnIndex = destinationIndices.get(columnIndex);
                    MapDistanceElementResponse element = providerRows.get(rowIndex).elements().get(columnIndex);
                    rows.get(originalRowIndex).elements().set(originalColumnIndex, element);
                }
            }
        }

        if ("vietmap".equals(source) && hasMissingRoutes(rows)) {
            return null;
        }
        return new MapDistanceMatrixResponse("OK", source, origins, destinations, rows);
    }

    private String appendDestinationContext(String location, String destinationContext) {
        boolean alreadyContainsContext = location.toLowerCase(Locale.ROOT)
                .contains(destinationContext.toLowerCase(Locale.ROOT));
        if (destinationContext.isBlank() || location.contains(",") || alreadyContainsContext) {
            return location;
        }
        return location + ", " + destinationContext;
    }

    private List<Integer> findResolvedIndices(List<String> locations,
            Map<String, MapCoordinatesResponse> resolvedCoordinates) {
        List<Integer> indices = new ArrayList<>();
        for (int index = 0; index < locations.size(); index++) {
            if (resolvedCoordinates.get(locations.get(index)) != null) {
                indices.add(index);
            }
        }
        return indices;
    }

    private List<MapDistanceRowResponse> createEmptyRows(int originCount, int destinationCount, String source) {
        List<MapDistanceRowResponse> rows = new ArrayList<>();
        for (int originIndex = 0; originIndex < originCount; originIndex++) {
            List<MapDistanceElementResponse> elements = new ArrayList<>();
            for (int destinationIndex = 0; destinationIndex < destinationCount; destinationIndex++) {
                elements.add(new MapDistanceElementResponse("ZERO_RESULTS", source, null, null));
            }
            rows.add(new MapDistanceRowResponse(elements));
        }
        return rows;
    }

    private boolean hasMissingRoutes(List<MapDistanceRowResponse> rows) {
        for (MapDistanceRowResponse row : rows) {
            for (MapDistanceElementResponse element : row.elements()) {
                if (!"OK".equals(element.status())) {
                    return true;
                }
            }
        }
        return false;
    }

    private <T> T withFallback(Supplier<T> vietmapRequest, Supplier<T> osmRequest) {
        checkInterrupted();
        if (vietmapClient.configured() && clock.millis() >= retryVietmapAt) {
            try {
                T result = vietmapRequest.get();
                if (result != null) {
                    return result;
                }
            } catch (MapsException exception) {
                checkInterrupted();
                retryVietmapAt = clock.millis() + VIETMAP_RETRY_DELAY_MILLIS;
            }
        }
        checkInterrupted();
        return osmRequest.get();
    }

    private MapCoordinatesResponse parseCoordinates(String value) {
        Matcher match = COORDINATES.matcher(value);
        if (!match.matches()) {
            return null;
        }
        double latitude = Double.parseDouble(match.group(1));
        double longitude = Double.parseDouble(match.group(2));
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
            throw MapsException.invalid("Invalid coordinates.");
        }
        return new MapCoordinatesResponse(latitude, longitude);
    }

    private List<String> readLocations(JsonNode value) {
        if (value == null || !value.isArray() || value.isEmpty() || value.size() > 25) {
            throw MapsException.invalid("Provide 1 to 25 locations per side.");
        }
        List<String> locations = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isTextual()) {
                throw MapsException.invalid("Locations must be text.");
            }
            String location = validateText(item.asText(), 500);
            if (location.isBlank()) {
                throw MapsException.invalid("Locations must not be empty.");
            }
            locations.add(location);
        }
        return locations;
    }

    private String validateText(String value, int maxLength) {
        String result = value == null ? "" : value.trim();
        if (result.length() > maxLength) {
            throw MapsException.invalid("Search text or address is too long.");
        }
        return result;
    }

    private String normalizeLanguage(String value) {
        return "en".equals(value) ? "en" : "vi";
    }

    private void requireUser(Long currentUserId) {
        if (currentUserId == null || currentUserId <= 0) {
            throw new MapsException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized");
        }
    }

    private void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw MapsException.provider();
        }
    }
}
