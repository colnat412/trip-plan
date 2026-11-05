package com.tripbudget.tripbudget_core.maps.dtos.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record MapDistanceMatrixResponse(
        String status,
        String source,
        @JsonProperty("origin_addresses") List<String> originAddresses,
        @JsonProperty("destination_addresses") List<String> destinationAddresses,
        List<MapDistanceRowResponse> rows
) {
}
