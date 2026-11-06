package com.tripbudget.tripbudget_core.auth.dtos.response;


public record RefreshResponse(
        String accessToken
) {
    @Override
    public String toString() {
        return "RefreshResponse[accessToken=REDACTED]";
    }
}
