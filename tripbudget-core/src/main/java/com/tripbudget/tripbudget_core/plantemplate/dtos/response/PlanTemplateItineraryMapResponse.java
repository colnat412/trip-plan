package com.tripbudget.tripbudget_core.plantemplate.dtos.response;

import java.util.List;

public record PlanTemplateItineraryMapResponse(List<Stop> items, PlanTemplateMapResponse.Pagination pagination) {
    public record Stop(String id, String label, int order, Double latitude, Double longitude) {}
}
