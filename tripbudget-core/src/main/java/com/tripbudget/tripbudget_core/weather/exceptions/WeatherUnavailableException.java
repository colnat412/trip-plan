package com.tripbudget.tripbudget_core.weather.exceptions;

/** Sanitized diagnostic information; never retain provider URLs, bodies or secret-bearing causes. */
public class WeatherUnavailableException extends IllegalStateException {
    private final String stage;
    private final String reason;

    public WeatherUnavailableException(String stage, String reason) {
        super(reason);
        this.stage = stage;
        this.reason = reason;
    }

    public String stage() {
        return stage;
    }

    public String reason() {
        return reason;
    }
}
