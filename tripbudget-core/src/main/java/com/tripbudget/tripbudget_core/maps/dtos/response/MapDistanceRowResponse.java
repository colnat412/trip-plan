package com.tripbudget.tripbudget_core.maps.dtos.response;

import java.util.List;

public record MapDistanceRowResponse(
        List<MapDistanceElementResponse> elements
) {
}
