package com.tripbudget.tripbudget_core.trip.repositories.projections;

import java.time.LocalDate;
import com.tripbudget.tripbudget_core.trip.enums.TripStatus;

public interface TripFilterOption {
    String getDestination();
    String getCurrency();
    LocalDate getEndDate();
    TripStatus getStatus();
}
