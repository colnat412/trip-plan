package com.tripbudget.tripbudget_core.maps.dtos.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MapDistanceElementResponse(
        String status,
        String source,
        MapMeasurementResponse distance,
        MapMeasurementResponse duration
) {
}
