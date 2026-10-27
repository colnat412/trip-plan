package com.tripbudget.tripbudget_core.plantemplate.controllers;

import com.tripbudget.tripbudget_core.common.dtos.ApiResponse;
import org.springframework.core.annotation.Order;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(0)
@RestControllerAdvice(assignableTypes = PlanTemplateController.class)
public class TemplateConflictHandler {
    @ExceptionHandler({ConcurrencyFailureException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ApiResponse<Void>> conflict(Exception exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(
                HttpStatus.CONFLICT, "TEMPLATE_CONFLICT_REVIEW_AND_RETRY"
        ));
    }
}
