package com.tripbudget.tripbudget_core.trip.enums;

public enum TripMemberRole {
    OWNER,
    VICE,
    MEMBER;

    public boolean canManageTrip() {
        return this == OWNER || this == VICE;
    }
}
