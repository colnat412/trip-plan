package com.tripbudget.tripbudget_core.maps.web;

import org.springframework.http.HttpStatus;

public class MapsException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public MapsException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }
    public String code() {
        return code;
    }

    public static MapsException provider() {
        return new MapsException(HttpStatus.BAD_GATEWAY, "MAPS_PROVIDER_ERROR", "Map provider is unavailable. Please try again.");
    }
    public static MapsException invalid(String message) {
        return new MapsException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }
}
