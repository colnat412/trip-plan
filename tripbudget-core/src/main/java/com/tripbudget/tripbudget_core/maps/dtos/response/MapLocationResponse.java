package com.tripbudget.tripbudget_core.maps.dtos.response;

public record MapLocationResponse(
        MapCoordinatesResponse coordinates,
        String source
) {
}
