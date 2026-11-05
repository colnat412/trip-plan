package com.tripbudget.tripbudget_core.maps.controllers;

import com.tripbudget.tripbudget_core.common.annotations.CurrentUser;
import com.tripbudget.tripbudget_core.common.dtos.ApiResponse;
import com.tripbudget.tripbudget_core.common.dtos.CurrentUserDto;
import com.tripbudget.tripbudget_core.maps.dtos.request.MapDistanceRequest;
import com.tripbudget.tripbudget_core.maps.dtos.response.MapDistanceMatrixResponse;
import com.tripbudget.tripbudget_core.maps.dtos.response.MapLocationResultResponse;
import com.tripbudget.tripbudget_core.maps.dtos.response.MapSearchResponse;
import com.tripbudget.tripbudget_core.maps.services.MapsService;
import com.tripbudget.tripbudget_core.maps.web.MapsException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/maps")
@RequiredArgsConstructor
public class MapsController {

    private final MapsService mapsService;

    @GetMapping("/places")
    public ResponseEntity<ApiResponse<MapSearchResponse>> places(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "vi") String lang,
            @RequestParam(defaultValue = "false") String regionsOnly,
            @CurrentUser CurrentUserDto user
    ) {
        Long currentUserId = user.id();
        if (!"true".equals(regionsOnly) && !"false".equals(regionsOnly)) {
            throw MapsException.invalid("regionsOnly must be true or false.");
        }
        MapSearchResponse response = mapsService.search(currentUserId, q, lang, Boolean.parseBoolean(regionsOnly));
        return success(response);
    }

    @GetMapping("/location")
    public ResponseEntity<ApiResponse<MapLocationResultResponse>> location(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "vi") String lang,
            @CurrentUser CurrentUserDto user
    ) {
        Long currentUserId = user.id();
        MapLocationResultResponse response = mapsService.location(currentUserId, q, lang);
        return success(response);
    }

    @PostMapping("/distance")
    public ResponseEntity<ApiResponse<MapDistanceMatrixResponse>> distance(
            @RequestBody MapDistanceRequest request,
            @CurrentUser CurrentUserDto user
    ) {
        Long currentUserId = user.id();
        MapDistanceMatrixResponse response = mapsService.distance(currentUserId, request);
        return success(response);
    }

    private <T> ResponseEntity<ApiResponse<T>> success(T data) {
        return ResponseEntity.ok()
                .header("Cache-Control", "private, no-store")
                .body(ApiResponse.success(HttpStatus.OK, "Map request completed", data));
    }
}
