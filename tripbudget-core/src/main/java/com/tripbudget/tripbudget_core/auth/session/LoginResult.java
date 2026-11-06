package com.tripbudget.tripbudget_core.auth.session;

import com.tripbudget.tripbudget_core.auth.dtos.response.AuthResponse;

/** Internal result. The refresh token is written to a cookie, never serialized as the API body. */
public record LoginResult(AuthResponse response, String refreshToken) {
    @Override
    public String toString() {
        return "LoginResult[tokens=REDACTED]";
    }
}
