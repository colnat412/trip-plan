package com.tripbudget.tripbudget_core.weather.services;

import com.tripbudget.tripbudget_core.maps.services.MapsService;
import com.tripbudget.tripbudget_core.trip.repositories.TripRepository;
import com.tripbudget.tripbudget_core.trip.services.TripPermissionService;
import com.tripbudget.tripbudget_core.trip.enums.TripStatus;
import com.tripbudget.tripbudget_core.weather.clients.OpenMeteoClient;
import com.tripbudget.tripbudget_core.weather.dtos.response.*;
import com.tripbudget.tripbudget_core.weather.enums.WeatherCondition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.tripbudget.tripbudget_core.weather.exceptions.WeatherUnavailableException;
import com.tripbudget.tripbudget_core.maps.web.MapsException;
import com.tripbudget.tripbudget_core.maps.dtos.response.MapLocationResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.HexFormat;

@Service
@Slf4j
@RequiredArgsConstructor
public class WeatherService {
    private final TripRepository trips;
    private final TripPermissionService permissions;
    private final MapsService maps;
    private final OpenMeteoClient client;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    // Bounded locks collapse concurrent loads on this project's single Core instance.
    private final Object[] locks = java.util.stream.IntStream.range(0, 32).mapToObj(i -> new Object()).toArray();

    public TripWeatherResponse get(Long userId, Long tripId) {
        permissions.requireActiveMember(tripId, userId);
        var trip = trips.findActiveTrip(tripId);
        if (trip == null || trip.isDel()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
        LocalDate date = trip.getStartDate();
        if (trip.getStatus() == TripStatus.CANCELLED) {
            return new TripWeatherResponse("HIDDEN", date, null, null);
        }
        if (date == null) return new TripWeatherResponse("UNAVAILABLE", null, null, null);
        // UTC +/- one day covers every destination timezone without guessing its timezone.
        LocalDate utcToday = LocalDate.now(ZoneOffset.UTC);
        if (date.isBefore(utcToday.minusDays(1))) return new TripWeatherResponse("PAST", date, null, null);
        if (date.isAfter(utcToday.plusDays(16))) return new TripWeatherResponse("OUT_OF_RANGE", date, null, null);
        String destination = trip.getDestination();
        if (destination == null || destination.isBlank() || destination.length() > 500
                || destination.matches("(?s).*(?:→|;|\n| -> ).*")) {
            return new TripWeatherResponse("LOCATION_UNAVAILABLE", date, null, null);
        }
        try {
            WeatherForecastResponse forecast = load(userId, destination.trim());
            if (forecast == null) return new TripWeatherResponse("LOCATION_UNAVAILABLE", date, null, null);
            return select(date, trip.getEndDate(), forecast, Instant.now());
        } catch (WeatherUnavailableException exception) {
            log.warn("Trip weather unavailable: tripId={}, stage={}, reason={}",
                    tripId, exception.stage(), exception.reason());
            return new TripWeatherResponse("UNAVAILABLE", date, null, null, exception.reason());
        } catch (RuntimeException exception) {
            log.warn("Trip weather unavailable: tripId={}, stage=SUMMARY, errorType={}",
                    tripId, exception.getClass().getSimpleName());
            return new TripWeatherResponse("UNAVAILABLE", date, null, null, "WEATHER_INTERNAL_ERROR");
        }
    }

    public TripWeatherResponse select(LocalDate date, LocalDate endDate,
                                      WeatherForecastResponse forecast, Instant now) {
        LocalDate today = now.atZone(ZoneId.of(forecast.timezone())).toLocalDate();
        if (date.isBefore(today)) return new TripWeatherResponse("PAST", date, null, null);
        if (endDate == null || endDate.isBefore(date)) {
            return new TripWeatherResponse("UNAVAILABLE", date, null, null);
        }
        LocalDate summaryEnd = endDate.isBefore(date.plusDays(2)) ? endDate : date.plusDays(2);
        int dayCount = (int) java.time.temporal.ChronoUnit.DAYS.between(date, summaryEnd) + 1;
        double totalTemperature = 0;
        WeatherCondition condition = WeatherCondition.UNKNOWN;
        for (int offset = 0; offset < dayCount; offset++) {
            LocalDate expectedDate = date.plusDays(offset);
            var day = forecast.days().stream().filter(value -> value.date().equals(expectedDate))
                    .findFirst().orElse(null);
            // Do not present a partial forecast as a summary of all three days.
            if (day == null) return new TripWeatherResponse("OUT_OF_RANGE", date, null, null);
            totalTemperature += day.meanTempC();
            condition = day.condition().moreDisruptiveThan(condition);
        }
        var summary = new WeatherSummaryResponse(date, summaryEnd, dayCount, totalTemperature / dayCount, condition);
        return new TripWeatherResponse("READY", date, null, summary);
    }

    private WeatherForecastResponse load(Long userId, String destination) {
        String key = "weather:forecast:openmeteo:v3:" + digest(destination);
        synchronized (locks[Math.floorMod(key.hashCode(), locks.length)]) {
            String cached;
            try {
                cached = redis.opsForValue().get(key);
            } catch (RuntimeException exception) {
                throw new WeatherUnavailableException("CACHE_READ", "WEATHER_CACHE_UNAVAILABLE");
            }
            if ("unavailable".equals(cached)) {
                throw new WeatherUnavailableException("FAILURE_CACHE", "WEATHER_RECENT_FAILURE");
            }
            if (cached != null && cached.startsWith("unavailable:")) {
                String[] failure = cached.split(":", 3);
                if (failure.length == 3 && failure[1].matches("[A-Z_]+") && failure[2].matches("[A-Z_0-9]+")) {
                    throw new WeatherUnavailableException(failure[1], failure[2]);
                }
                throw new WeatherUnavailableException("FAILURE_CACHE", "WEATHER_RECENT_FAILURE");
            }
            if (cached != null) {
                try {
                    return mapper.readValue(cached, WeatherForecastResponse.class);
                } catch (RuntimeException exception) {
                    // An old or damaged cache entry must not break weather until its TTL expires.
                    log.warn("Ignoring invalid trip weather cache entry: reason=WEATHER_CACHE_INVALID");
                }
            }
            try {
                var location = resolveLocation(userId, destination);
                if (location == null) return null;
                var coordinates = location.coordinates();
                var forecast = client.forecast(coordinates.latitude(), coordinates.longitude());
                try {
                    redis.opsForValue().set(key, mapper.writeValueAsString(forecast), Duration.ofHours(1));
                } catch (RuntimeException exception) {
                    // The successful provider result can still be rendered on this request.
                    log.warn("Trip weather cache write failed: reason=WEATHER_CACHE_UNAVAILABLE");
                }
                return forecast;
            } catch (WeatherUnavailableException exception) {
                try {
                    redis.opsForValue().set(key, "unavailable:" + exception.stage() + ":" + exception.reason(), Duration.ofMinutes(1));
                } catch (RuntimeException cacheException) {
                    log.warn("Trip weather failure cache write failed: reason=WEATHER_CACHE_UNAVAILABLE");
                }
                throw exception;
            }
        }
    }

    private MapLocationResponse resolveLocation(Long userId, String destination) {
        try {
            return maps.location(userId, destination, "vi").location();
        } catch (MapsException exception) {
            String reason = "MAPS_RATE_LIMITED".equals(exception.code())
                    ? "WEATHER_LOCATION_RATE_LIMITED" : "WEATHER_LOCATION_UNAVAILABLE";
            throw new WeatherUnavailableException("GEOCODING", reason);
        } catch (RuntimeException exception) {
            throw new WeatherUnavailableException("GEOCODING", "WEATHER_LOCATION_UNAVAILABLE");
        }
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
