package com.tripbudget.tripbudget_core.auth.dtos.response;

import com.tripbudget.tripbudget_core.user.dtos.response.UserProfileResponse;
public record AuthResponse(
        String accessToken, UserProfileResponse user
) {
    @Override
    public String toString() {
        return "AuthResponse[accessToken=REDACTED]";
    }
}
