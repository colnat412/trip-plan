package com.tripbudget.tripbudget_core.maps.dtos.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MapPlacePredictionResponse(
        String placeId,
        String mainText,
        String secondaryText,
        String description,
        String source,
        MapCoordinatesResponse coordinates
) {
}
