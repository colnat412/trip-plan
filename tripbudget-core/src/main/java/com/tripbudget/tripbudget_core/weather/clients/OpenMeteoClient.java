package com.tripbudget.tripbudget_core.weather.clients;

import com.tripbudget.tripbudget_core.weather.dtos.response.*;
import com.tripbudget.tripbudget_core.weather.enums.WeatherCondition;
import com.tripbudget.tripbudget_core.weather.exceptions.WeatherUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.ArrayList;

@Component
public class OpenMeteoClient {
    private final ObjectMapper mapper;
    private final HttpClient http;

    @Autowired
    public OpenMeteoClient(ObjectMapper mapper) {
        this(mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    public OpenMeteoClient(ObjectMapper mapper, HttpClient http) {
        this.mapper = mapper;
        this.http = http;
    }

    public WeatherForecastResponse forecast(double latitude, double longitude) {
        try {
            if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                    || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
                throw new IllegalArgumentException("Invalid coordinates");
            }
            URI uri = URI.create("https://api.open-meteo.com/v1/forecast?latitude=" + latitude
                    + "&longitude=" + longitude + "&forecast_days=16&timezone=auto&temperature_unit=celsius"
                    + "&daily=temperature_2m_mean,weather_code");
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(6))
                    .header("Accept", "application/json").GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new WeatherUnavailableException("PROVIDER", "WEATHER_HTTP_" + response.statusCode());
            }
            if (response.body().length() > 1_000_000) {
                throw new WeatherUnavailableException("PROVIDER", "WEATHER_INVALID_RESPONSE");
            }
            return parse(response.body(), Instant.now());
        } catch (WeatherUnavailableException exception) {
            throw exception;
        } catch (HttpTimeoutException exception) {
            throw new WeatherUnavailableException("PROVIDER", "WEATHER_TIMEOUT");
        } catch (java.io.IOException exception) {
            throw new WeatherUnavailableException("PROVIDER", "WEATHER_NETWORK_ERROR");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new WeatherUnavailableException("PROVIDER", "WEATHER_INTERRUPTED");
        } catch (Exception exception) {
            // Keep upstream response bodies and transport internals out of API errors.
            throw new WeatherUnavailableException("PROVIDER", "WEATHER_INVALID_RESPONSE");
        }
    }

    public WeatherForecastResponse parse(String body, Instant fetchedAt) {
        JsonNode json = mapper.readTree(body);
        String timezone = json.path("timezone").asText("");
        ZoneId.of(timezone);
        double latitude = number(json.path("latitude"));
        double longitude = number(json.path("longitude"));
        if (Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
            throw new IllegalArgumentException("Invalid forecast location");
        }
        JsonNode daily = json.path("daily");
        JsonNode dates = daily.path("time");
        if (!dates.isArray() || dates.isEmpty() || dates.size() > 16) {
            throw new IllegalArgumentException("Missing forecast dates");
        }
        JsonNode temperatures = daily.path("temperature_2m_mean");
        JsonNode codes = daily.path("weather_code");
        for (JsonNode values : java.util.List.of(temperatures, codes)) {
            if (!values.isArray() || values.size() != dates.size()) {
                throw new IllegalArgumentException("Mismatched forecast arrays");
            }
        }
        var days = new ArrayList<WeatherDayResponse>();
        LocalDate previous = null;
        for (int index = 0; index < dates.size(); index++) {
            LocalDate date = LocalDate.parse(dates.get(index).asText());
            if (previous != null && !date.equals(previous.plusDays(1))) {
                throw new IllegalArgumentException("Invalid forecast date sequence");
            }
            previous = date;
            double temperature = number(temperatures.get(index));
            if (temperature < -100 || temperature > 70) {
                throw new IllegalArgumentException("Invalid forecast temperature");
            }
            JsonNode code = codes.get(index);
            if (!code.isNull() && (!code.isIntegralNumber() || !code.canConvertToInt())) {
                throw new IllegalArgumentException("Invalid weather code");
            }
            WeatherCondition condition = WeatherCondition.fromWmo(code.isNull() ? null : code.asInt());
            days.add(new WeatherDayResponse(date, temperature, condition));
        }
        // Open-Meteo returns the weather model grid coordinates, not a city name.
        String location = String.format(java.util.Locale.ROOT, "%.4f, %.4f", latitude, longitude);
        return new WeatherForecastResponse(location, timezone, fetchedAt, java.util.List.copyOf(days));
    }

    private double number(JsonNode value) {
        if (!value.isNumber() || !Double.isFinite(value.asDouble())) {
            throw new IllegalArgumentException("Invalid forecast value");
        }
        return value.asDouble();
    }
}
