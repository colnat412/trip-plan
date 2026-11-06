package com.tripbudget.tripbudget_core.auth.dtos.request;


public record LoginRequest(
        String email, String password
) {
    @Override
    public String toString() {
        return "LoginRequest[credentials=REDACTED]";
    }
}
