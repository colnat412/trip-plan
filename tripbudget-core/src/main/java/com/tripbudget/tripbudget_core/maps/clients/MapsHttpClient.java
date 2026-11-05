package com.tripbudget.tripbudget_core.maps.clients;

import com.tripbudget.tripbudget_core.maps.config.MapsSettings;
import com.tripbudget.tripbudget_core.maps.web.MapsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

@Slf4j
@Component
public class MapsHttpClient {

    private static final int RESPONSE_LIMIT_BYTES = 1024 * 1024;
    private static final long PUBLIC_REQUEST_INTERVAL_MILLIS = 1000;
    private static final long PUBLIC_MAX_WAIT_MILLIS = 4000;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final MapsSettings settings;
    private final Map<String, Long> publicRequestSlots = new HashMap<>();

    @Autowired
    public MapsHttpClient(ObjectMapper objectMapper, MapsSettings settings) {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(), objectMapper, settings);
    }

    public MapsHttpClient(HttpClient httpClient, ObjectMapper objectMapper, MapsSettings settings) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.settings = settings;
    }

    public JsonNode get(String baseUrl, Map<String, List<String>> query, boolean publicServer) {
        try {
            URI uri = buildUri(baseUrl, query);
            if (!"https".equals(uri.getScheme()) && (settings.isRequireHttps() || !"http".equals(uri.getScheme()))) {
                throw MapsException.provider();
            }
            if (publicServer) {
                reservePublicSlot(uri.getHost());
            }
            if (Thread.currentThread().isInterrupted()) {
                throw MapsException.provider();
            }

            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .header("Accept", "application/json")
                    .header("User-Agent", "TripPlan/1.0 (travel itinerary maps)")
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            return readResponse(uri, response);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw MapsException.provider();
        } catch (MapsException exception) {
            throw exception;
        } catch (Exception exception) {
            throw MapsException.provider();
        }
    }

    private URI buildUri(String baseUrl, Map<String, List<String>> query) {
        StringJoiner encodedQuery = new StringJoiner("&");
        for (Map.Entry<String, List<String>> parameter : query.entrySet()) {
            String encodedName = URLEncoder.encode(parameter.getKey(), StandardCharsets.UTF_8);
            for (String value : parameter.getValue()) {
                String encodedValue = URLEncoder.encode(value, StandardCharsets.UTF_8);
                encodedQuery.add(encodedName + "=" + encodedValue);
            }
        }
        String separator = baseUrl.contains("?") ? "&" : "?";
        return URI.create(baseUrl + separator + encodedQuery);
    }

    private JsonNode readResponse(URI uri, HttpResponse<InputStream> response) throws Exception {
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) {
                if ("maps.vietmap.vn".equals(uri.getHost())) {
                    log.warn("Vietmap request failed: HTTP {}. For HTTP 423, check API access and remaining quota in Vietmap Console.",
                            response.statusCode());
                }
                throw MapsException.provider();
            }
            byte[] bytes = body.readNBytes(RESPONSE_LIMIT_BYTES + 1);
            if (bytes.length > RESPONSE_LIMIT_BYTES) {
                throw MapsException.provider();
            }
            JsonNode json = objectMapper.readTree(bytes);
            if (json == null) {
                throw MapsException.provider();
            }
            return json;
        }
    }

    private void reservePublicSlot(String host) throws InterruptedException {
        long waitMillis;
        synchronized (publicRequestSlots) {
            long now = System.currentTimeMillis();
            long nextSlot = Math.max(now, publicRequestSlots.getOrDefault(host, now));
            waitMillis = nextSlot - now;
            if (waitMillis > PUBLIC_MAX_WAIT_MILLIS) {
                throw new MapsException(HttpStatus.SERVICE_UNAVAILABLE, "MAPS_BUSY", "Map search is busy. Please try again.");
            }
            publicRequestSlots.put(host, nextSlot + PUBLIC_REQUEST_INTERVAL_MILLIS);
        }
        if (waitMillis > 0) {
            Thread.sleep(waitMillis);
        }
    }
}
