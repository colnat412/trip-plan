package com.tripbudget.tripbudget_core.user.dtos.response;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record UserProfileResponse(
        String id, String email, String name, String avatarUrl,
        Instant policiesAcceptedAt, String policiesVersion, String requiredPolicyVersion
) {}
