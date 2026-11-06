package com.tripbudget.tripbudget_core.auth.session;


public record SessionTokens(
        String accessToken, String refreshToken
) {
    @Override
    public String toString() {
        return "SessionTokens[tokens=REDACTED]";
    }
}
