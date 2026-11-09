package com.tripbudget.tripbudget_core.weather.controllers;

import com.tripbudget.tripbudget_core.common.annotations.CurrentUser;
import com.tripbudget.tripbudget_core.common.dtos.*;
import com.tripbudget.tripbudget_core.common.services.HashidsService;
import com.tripbudget.tripbudget_core.weather.services.WeatherService;
import com.tripbudget.tripbudget_core.weather.dtos.response.TripWeatherResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/trip/{tripId}/weather")
@RequiredArgsConstructor
public class WeatherController {
    private final WeatherService weather;
    private final HashidsService hashids;

    @GetMapping
    public ResponseEntity<ApiResponse<TripWeatherResponse>> get(@CurrentUser CurrentUserDto user,
                                                              @PathVariable String tripId) {
        Long currentUserId = user.id();
        var response = weather.get(currentUserId, hashids.decode(tripId));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(HttpStatus.OK, "Trip forecast", response));
    }
}
