package com.tripbudget.tripbudget_core.common.exceptions;

import com.tripbudget.tripbudget_core.common.dtos.ApiResponse;
import com.tripbudget.tripbudget_core.auth.controllers.AuthController;
import com.tripbudget.tripbudget_core.uploads.controllers.UploadController;
import com.tripbudget.tripbudget_core.user.controllers.UserController;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.HandlerMapping;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidateException(
            MethodArgumentNotValidException exception, HttpServletRequest request)
    {
        if (isIdentityRequest(request)) {
            var messages = exception.getBindingResult().getFieldErrors().stream()
                    .map(field -> field.getField() + " " + field.getDefaultMessage()).toList();
            return identityError(HttpStatus.BAD_REQUEST, messages);
        }
        Map<String, String> errors = new LinkedHashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(e -> errors.putIfAbsent(
                        e.getField(),
                        e.getDefaultMessage()
                ));
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(
                        ApiResponse.failure(
                                HttpStatus.BAD_REQUEST,
                                "Validation failure",
                                errors
                        )
                );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleUnreadableRequest(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        if (isIdentityRequest(request)) {
            return identityError(HttpStatus.BAD_REQUEST, "Invalid request body");
        }
        return ResponseEntity.badRequest().body(ApiResponse.error(
                HttpStatus.BAD_REQUEST, "Invalid request body or unsupported enum value"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> handleResponseStatusException(
            ResponseStatusException exception, HttpServletRequest request)
    {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        if (isIdentityRequest(request)) {
            return identityError(status, exception.getReason());
        }
        return ResponseEntity
                .status(status)
                .body(
                        ApiResponse.error(
                                status,
                                exception.getReason() == null
                                        ? "Request failed"
                                        : exception.getReason()
                        )
                );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> handleUploadLimit(
            MaxUploadSizeExceededException exception, HttpServletRequest request) {
        if (isIdentityRequest(request)) {
            return identityError(HttpStatus.PAYLOAD_TOO_LARGE, "File too large");
        }
        return handleGeneralException(exception);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<?> handleRequestParameters(Exception exception, HttpServletRequest request) {
        if (isIdentityRequest(request)) {
            return identityError(HttpStatus.BAD_REQUEST, "Invalid request parameters");
        }
        return handleGeneralException(exception);
    }

    // Preserve the former NestJS error contract without changing errors from other Core controllers.
    private boolean isIdentityRequest(HttpServletRequest request) {
        Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (!(handler instanceof HandlerMethod method)) {
            return false;
        }
        Class<?> controller = method.getBeanType();
        return AuthController.class.isAssignableFrom(controller)
                || UserController.class.isAssignableFrom(controller)
                || UploadController.class.isAssignableFrom(controller);
    }

    private ResponseEntity<Map<String, Object>> identityError(HttpStatus status, Object message) {
        return ResponseEntity.status(status).body(Map.of(
                "statusCode", status.value(),
                "message", message == null ? status.getReasonPhrase() : message,
                "error", status.getReasonPhrase()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception exception)
    {
        log.error("Unhandled Core API error", exception);
        return  ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        ApiResponse.error(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Internal server error"
                        )
                );
    }
}
