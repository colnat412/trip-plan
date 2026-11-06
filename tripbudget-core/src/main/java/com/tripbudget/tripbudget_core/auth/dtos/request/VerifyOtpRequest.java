package com.tripbudget.tripbudget_core.auth.dtos.request;


public record VerifyOtpRequest(
        String email, String otp
) {
    @Override
    public String toString() {
        return "VerifyOtpRequest[otp=REDACTED]";
    }
}
