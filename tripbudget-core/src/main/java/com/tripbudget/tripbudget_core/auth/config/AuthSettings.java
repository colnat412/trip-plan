package com.tripbudget.tripbudget_core.auth.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class AuthSettings {
    public static final long ACCESS_TTL_SECONDS = 15 * 60;
    public static final long REFRESH_TTL_SECONDS = 30 * 24 * 60 * 60;

    private final String jwtSecret;
    private final String refreshPepper;
    private final boolean cookieSecure;
    private final long cookieTtlSeconds;
    private final String googleClientId;

    public AuthSettings(
            @Value("${security.jwt.secret}") String jwtSecret,
            @Value("${REFRESH_TOKEN_PEPPER:default_pepper}") String refreshPepper,
            @Value("${COOKIE_SECURE:false}") boolean cookieSecure,
            @Value("${REFRESH_TOKEN_TTL_SECONDS:2592000}") long cookieTtlSeconds,
            @Value("${GOOGLE_CLIENT_ID:}") String googleClientId
    ) {
        this.jwtSecret = jwtSecret;
        this.refreshPepper = refreshPepper.isEmpty() ? "default_pepper" : refreshPepper;
        this.cookieSecure = cookieSecure;
        this.cookieTtlSeconds = cookieTtlSeconds;
        this.googleClientId = googleClientId;
    }
}
