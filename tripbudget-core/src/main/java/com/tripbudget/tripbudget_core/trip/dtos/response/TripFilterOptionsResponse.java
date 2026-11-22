package com.tripbudget.tripbudget_core.trip.dtos.response;

import java.util.List;

public record TripFilterOptionsResponse(List<String> destinations, List<String> currencies,
                                        List<String> visitedDestinations) {}
