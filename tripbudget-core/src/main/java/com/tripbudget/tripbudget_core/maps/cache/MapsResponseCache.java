package com.tripbudget.tripbudget_core.maps.cache;

import com.tripbudget.tripbudget_core.maps.dtos.response.MapDistanceMatrixResponse;
import com.tripbudget.tripbudget_core.maps.dtos.response.MapLocationResultResponse;
import com.tripbudget.tripbudget_core.maps.dtos.response.MapSearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/** Provider data only. Call after authentication, request validation and request budgets. */
@Component
@Slf4j
public class MapsResponseCache {
    private static final int MAX_LOCAL_ENTRIES = 256;
    private static final int MAX_LOCAL_BYTES = 8 * 1024 * 1024;

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Clock clock;
    // Matrix loading calls geocoding: separate lock groups prevent nested lock inversions.
    private final java.util.Map<String, Object[]> locks = java.util.Map.of(
            "search", createLocks(), "location", createLocks(), "distance", createLocks());
    private final LinkedHashMap<String, CachedResponse> local = new LinkedHashMap<>(16, 0.75f, true);
    private int localBytes;

    private record CachedResponse(String json, long expiresAt, int bytes) {}

    @Autowired
    public MapsResponseCache(StringRedisTemplate redis, ObjectMapper mapper) {
        this(redis, mapper, Clock.systemUTC());
    }

    public MapsResponseCache(StringRedisTemplate redis, ObjectMapper mapper, Clock clock) {
        this.redis = redis;
        this.mapper = mapper;
        this.clock = clock;
    }

    public MapSearchResponse search(String query, String language, boolean regionsOnly, Supplier<MapSearchResponse> fetch) {
        return cached(List.of("search", query, language, regionsOnly), MapSearchResponse.class,
                result -> result.predictions().isEmpty() ? Duration.ofSeconds(30) : Duration.ofMinutes(5), fetch);
    }

    public MapLocationResultResponse location(String address, String language, String source, Supplier<MapLocationResultResponse> fetch) {
        return cached(List.of("location", address, language, source), MapLocationResultResponse.class,
                result -> result.location() == null ? Duration.ofSeconds(30) : Duration.ofHours(24), fetch);
    }

    public MapDistanceMatrixResponse distance(List<String> origins, List<String> destinations, String language,
                                              String context, Supplier<MapDistanceMatrixResponse> fetch) {
        // List order is significant: A -> B and B -> A are different routes.
        return cached(List.of("distance", origins, destinations, language, context), MapDistanceMatrixResponse.class,
                result -> Duration.ofMinutes(15), fetch);
    }

    private <T> T cached(List<?> parameters, Class<T> type, Function<T, Duration> lifetime, Supplier<T> fetch) {
        String key = "maps:response:v1:" + digest(mapper.writeValueAsString(parameters));
        Object[] operationLocks = locks.get(parameters.getFirst());
        synchronized (operationLocks[Math.floorMod(key.hashCode(), operationLocks.length)]) {
            String json = readLocal(key);
            if (json == null) {
                try {
                    json = redis.opsForValue().get(key);
                } catch (RuntimeException exception) {
                    log.warn("Map response cache read unavailable; using bounded local cache");
                }
            }
            if (json != null) {
                try {
                    T result = mapper.readValue(json, type);
                    if (result != null) return result;
                } catch (RuntimeException exception) {
                    log.warn("Ignoring invalid map response cache entry");
                }
            }
            T result = fetch.get();
            // Exceptions are not persisted as successful provider responses.
            if (result != null) {
                Duration ttl = lifetime.apply(result);
                String encoded = mapper.writeValueAsString(result);
                writeLocal(key, encoded, ttl);
                try {
                    redis.opsForValue().set(key, encoded, ttl);
                } catch (RuntimeException exception) {
                    log.warn("Map response cache write unavailable; result retained in bounded local cache");
                }
            }
            return result;
        }
    }

    private synchronized String readLocal(String key) {
        CachedResponse response = local.get(key);
        if (response == null) return null;
        if (response.expiresAt() <= clock.millis()) {
            local.remove(key);
            localBytes -= response.bytes();
            return null;
        }
        return response.json();
    }

    private synchronized void writeLocal(String key, String json, Duration ttl) {
        // Approximate retained UTF-16 string storage, including the hashed key.
        int bytes = (json.length() + key.length()) * 2;
        if (bytes > MAX_LOCAL_BYTES) return;
        CachedResponse previous = local.remove(key);
        if (previous != null) localBytes -= previous.bytes();
        while (!local.isEmpty() && (local.size() >= MAX_LOCAL_ENTRIES || localBytes + bytes > MAX_LOCAL_BYTES)) {
            var oldest = local.entrySet().iterator();
            localBytes -= oldest.next().getValue().bytes();
            oldest.remove();
        }
        local.put(key, new CachedResponse(json, clock.millis() + ttl.toMillis(), bytes));
        localBytes += bytes;
    }

    private static Object[] createLocks() {
        return java.util.stream.IntStream.range(0, 32).mapToObj(i -> new Object()).toArray();
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }
}
