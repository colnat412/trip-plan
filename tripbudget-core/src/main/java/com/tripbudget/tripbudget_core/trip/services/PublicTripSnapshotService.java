package com.tripbudget.tripbudget_core.trip.services;

import com.tripbudget.tripbudget_core.plan.services.PlanService;
import com.tripbudget.tripbudget_core.trip.dtos.response.PublicTripResponse;
import com.tripbudget.tripbudget_core.trip.dtos.response.PublicTripSnapshot;
import com.tripbudget.tripbudget_core.trip.entities.TripEntity;
import com.tripbudget.tripbudget_core.trip.enums.TripVisibility;
import com.tripbudget.tripbudget_core.trip.events.PublicTripChangedEvent;
import com.tripbudget.tripbudget_core.trip.repositories.TripRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;

@Slf4j
@Service
public class PublicTripSnapshotService {

    private static final String SNAPSHOT_KEY_PREFIX = "public-trip:snapshot:v2:";
    private static final Duration SNAPSHOT_TTL = Duration.ofHours(1);
    private static final Duration UNAVAILABLE_TTL = Duration.ofMinutes(1);
    private static final String NOT_FOUND_MARKER = "NOT_FOUND";
    private static final String PRIVATE_MARKER = "PRIVATE";
    private static final Pattern SHARE_TOKEN_PATTERN = Pattern.compile("^[a-f0-9]{32}$");

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final TripRepository tripRepository;
    private final PlanService planService;
    private final TransactionTemplate readOnlyTx;

    public PublicTripSnapshotService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            TripRepository tripRepository,
            PlanService planService,
            PlatformTransactionManager transactionManager
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.tripRepository = tripRepository;
        this.planService = planService;
        this.readOnlyTx = new TransactionTemplate(transactionManager);
        this.readOnlyTx.setReadOnly(true);
        this.readOnlyTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public PublicTripSnapshot getSnapshot(String shareToken) {
        if (shareToken == null || !SHARE_TOKEN_PATTERN.matcher(shareToken).matches()) {
            throw notFound();
        }

        String cached = safeGet(key(shareToken));
        if (cached != null) {
            return fromCached(cached);
        }

        String value = readOnlyTx.execute(status -> buildCacheValue(shareToken));
        safeSet(key(shareToken), value, isMarker(value) ? UNAVAILABLE_TTL : SNAPSHOT_TTL);
        return fromCached(value);
    }

    @TransactionalEventListener
    public void onPublicTripChanged(PublicTripChangedEvent event) {
        try {
            if (event.revokedShareToken() != null) {
                safeDelete(key(event.revokedShareToken()));
            }

            readOnlyTx.executeWithoutResult(status -> {
                TripEntity trip = tripRepository.findById(event.tripId()).orElse(null);
                if (trip == null || trip.getShareToken() == null) {
                    return;
                }
                String value = toCacheValue(trip);
                safeSet(key(trip.getShareToken()), value, SNAPSHOT_TTL);
            });
        } catch (Exception e) {
            log.warn("Cannot refresh public trip snapshot for tripId={}", event.tripId(), e);
        }
    }

    private String buildCacheValue(String shareToken) {
        return tripRepository.findByShareTokenAndIsDelFalse(shareToken)
                .map(this::toCacheValue)
                .orElse(NOT_FOUND_MARKER);
    }

    private String toCacheValue(TripEntity trip) {
        if (trip.isDel()) {
            return NOT_FOUND_MARKER;
        }
        if (trip.getVisibility() != TripVisibility.PUBLIC) {
            return PRIVATE_MARKER;
        }
        PublicTripSnapshot snapshot = new PublicTripSnapshot(
                PublicTripResponse.from(trip),
                planService.buildPublicPlanOverview(trip),
                Instant.now()
        );
        return objectMapper.writeValueAsString(snapshot);
    }

    private PublicTripSnapshot fromCached(String value) {
        if (NOT_FOUND_MARKER.equals(value)) {
            throw notFound();
        }
        if (PRIVATE_MARKER.equals(value)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This trip is private. Only members can view it.");
        }
        return objectMapper.readValue(value, PublicTripSnapshot.class);
    }

    private static boolean isMarker(String value) {
        return NOT_FOUND_MARKER.equals(value) || PRIVATE_MARKER.equals(value);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found or link has expired");
    }

    private static String key(String shareToken) {
        return SNAPSHOT_KEY_PREFIX + shareToken;
    }


    private String safeGet(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis GET failed for {}", key, e);
            return null;
        }
    }

    private void safeSet(String key, String value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.warn("Redis SET failed for {}", key, e);
        }
    }

    private void safeDelete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis DEL failed for {}", key, e);
        }
    }
}
