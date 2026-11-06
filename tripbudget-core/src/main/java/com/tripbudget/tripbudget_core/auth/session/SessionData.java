package com.tripbudget.tripbudget_core.auth.session;

import java.time.Instant;
public record SessionData(
        String userId, String refreshTokenHash, Instant createdAt, Instant expiresAt
) {}
