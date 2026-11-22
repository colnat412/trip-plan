package com.tripbudget.tripbudget_core.settlement.enums;

public enum SettlementStatus {
    PENDING, CONFIRMED, REJECTED, CANCELLED, DELETE_PENDING, DELETED;

    public boolean affectsBalance() {
        return this == CONFIRMED || this == DELETE_PENDING;
    }
}
