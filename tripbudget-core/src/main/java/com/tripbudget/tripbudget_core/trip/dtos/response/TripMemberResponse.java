package com.tripbudget.tripbudget_core.trip.dtos.response;

import com.tripbudget.tripbudget_core.trip.enums.TripMemberRole;
import com.tripbudget.tripbudget_core.trip.enums.TripMemberStatus;

import java.time.Instant;

public record TripMemberResponse(
        String id,
        String tripId,
        String userId,
        String name,
        String email,
        String avatarUrl,
        TripMemberRole role,
        TripMemberStatus status,
        Instant joinedAt,
        Instant createdAt,
        boolean requiresInviteeResponse
) {}
