package com.tripbudget.tripbudget_core.trip.dtos.response;

import com.tripbudget.tripbudget_core.trip.entities.TripEntity;
import com.tripbudget.tripbudget_core.trip.enums.TripMemberRole;
import com.tripbudget.tripbudget_core.trip.enums.TripStatus;
import com.tripbudget.tripbudget_core.trip.enums.TripVisibility;

import com.tripbudget.tripbudget_core.trip.enums.TripMemberStatus;

import java.time.LocalDate;

public record PublicTripResponse(
        String shareToken,
        String name,
        String destination,
        String description,
        String coverUrl,
        LocalDate startDate,
        LocalDate endDate,
        String baseCurrency,
        TripStatus status,
        TripVisibility visibility,
        TripMemberRole publicRole,
        TripMemberStatus currentUserStatus
) {
    public static PublicTripResponse from(TripEntity trip) {
        return from(trip, null);
    }

    public static PublicTripResponse from(TripEntity trip, TripMemberStatus currentUserStatus) {
        return new PublicTripResponse(
                trip.getShareToken(),
                trip.getName(),
                trip.getDestination(),
                trip.getDescription(),
                trip.getCoverUrl(),
                trip.getStartDate(),
                trip.getEndDate(),
                trip.getBaseCurrency(),
                trip.getStatus(),
                trip.getVisibility(),
                trip.getPublicRole(),
                currentUserStatus
        );
    }
}
