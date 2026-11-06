package com.tripbudget.tripbudget_core.auth.dtos.request;


public record GoogleLoginRequest(
        String credential
) {
    @Override
    public String toString() {
        return "GoogleLoginRequest[credential=REDACTED]";
    }
}
