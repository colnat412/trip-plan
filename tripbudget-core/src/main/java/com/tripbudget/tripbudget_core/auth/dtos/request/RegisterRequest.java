package com.tripbudget.tripbudget_core.auth.dtos.request;


public record RegisterRequest(
        String email, String password, String name
) {
    @Override
    public String toString() {
        return "RegisterRequest[credentials=REDACTED]";
    }
}
