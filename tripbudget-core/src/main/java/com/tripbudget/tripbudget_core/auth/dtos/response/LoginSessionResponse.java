package com.tripbudget.tripbudget_core.auth.dtos.response;

import java.time.Instant;

/** Explicit allowlist: no access token, refresh token/hash, raw user agent or database ID. */
public record LoginSessionResponse(String id, String clientType, String browser, String operatingSystem,
        String deviceType, String loginMethod, Instant createdAt, Instant lastRefreshedAt,
        Instant expiresAt, Instant endedAt, String status, boolean current) {}
