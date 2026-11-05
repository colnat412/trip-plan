package com.tripbudget.tripbudget_core.maps.web;

import com.tripbudget.tripbudget_core.common.dtos.ApiResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Order(-1)
@RestControllerAdvice
public class MapsExceptionHandler {
    @ExceptionHandler(MapsException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handle(MapsException error) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(error.status())
                .header("Cache-Control", "private, no-store");
        if (error.status().value() == 429) {
            response.header("Retry-After", "60");
        }
        return response.body(ApiResponse.failure(error.status(), error.getMessage(), Map.of("code", error.code())));
    }
}
