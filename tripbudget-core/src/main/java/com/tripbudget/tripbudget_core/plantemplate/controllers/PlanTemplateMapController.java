package com.tripbudget.tripbudget_core.plantemplate.controllers;

import com.tripbudget.tripbudget_core.common.dtos.ApiResponse;
import com.tripbudget.tripbudget_core.common.services.HashidsService;
import com.tripbudget.tripbudget_core.plantemplate.dtos.response.PlanTemplateItineraryMapResponse;
import com.tripbudget.tripbudget_core.plantemplate.dtos.response.PlanTemplateMapResponse;
import com.tripbudget.tripbudget_core.plantemplate.services.PlanTemplateMapService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class PlanTemplateMapController {
    private final PlanTemplateMapService maps;
    private final HashidsService ids;
    @GetMapping("/public/plan-templates/map")
    public ApiResponse<PlanTemplateMapResponse> list(@RequestParam(defaultValue = "") String q,
        @RequestParam(defaultValue = "") String category, @RequestParam(required = false) Double west,
        @RequestParam(required = false) Double south, @RequestParam(required = false) Double east,
        @RequestParam(required = false) Double north, @RequestParam(defaultValue = "0") int page) {
        return ApiResponse.success(HttpStatus.OK, "Published itineraries", maps.list(q, category, west, south, east, north, page));
    }

    @GetMapping("/public/plan-templates/{id}/map")
    public ApiResponse<PlanTemplateItineraryMapResponse> itinerary(
        @PathVariable String id, @RequestParam int day, @RequestParam(defaultValue = "0") int page,
        @RequestParam(required = false) Integer version) {
        return ApiResponse.success(HttpStatus.OK, "Template itinerary map", maps.itinerary(ids.decode(id), day, page, version));
    }

}
