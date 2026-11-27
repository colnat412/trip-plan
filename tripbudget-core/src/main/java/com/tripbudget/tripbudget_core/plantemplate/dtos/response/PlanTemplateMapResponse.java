package com.tripbudget.tripbudget_core.plantemplate.dtos.response;

import java.util.List;

public record PlanTemplateMapResponse(List<Itinerary> items, Pagination pagination) {
    // Geographic filtering follows cached geocoding; pages describe scanned published templates.
    public record Pagination(int page, int size, long totalElements, int totalPages, boolean hasNext) {}
    public record Itinerary(String id, String title, String destination, String coverUrl,
        int durationDays, long likeCount, Double latitude, Double longitude) {}
}
