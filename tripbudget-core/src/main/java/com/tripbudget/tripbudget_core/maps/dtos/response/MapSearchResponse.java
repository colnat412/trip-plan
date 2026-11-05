package com.tripbudget.tripbudget_core.maps.dtos.response;

import java.util.List;

public record MapSearchResponse(
        List<MapPlacePredictionResponse> predictions,
        String source
) {
}
