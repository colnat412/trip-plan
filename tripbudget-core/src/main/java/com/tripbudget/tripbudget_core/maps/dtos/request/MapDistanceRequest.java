package com.tripbudget.tripbudget_core.maps.dtos.request;

import tools.jackson.databind.JsonNode;

public record MapDistanceRequest(
        JsonNode origins,
        JsonNode destinations,
        JsonNode language,
        JsonNode destinationContext
) {
}
