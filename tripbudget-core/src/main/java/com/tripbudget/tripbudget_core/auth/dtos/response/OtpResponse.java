package com.tripbudget.tripbudget_core.auth.dtos.response;


public record OtpResponse(
        boolean success, String email, String message
) {}
