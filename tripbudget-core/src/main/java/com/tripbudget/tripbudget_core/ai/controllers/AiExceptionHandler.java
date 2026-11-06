package com.tripbudget.tripbudget_core.ai.controllers;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = AiController.class)
public class AiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("detail",
                exception.getReason() == null ? "AI request failed" : exception.getReason()));
    }
}
