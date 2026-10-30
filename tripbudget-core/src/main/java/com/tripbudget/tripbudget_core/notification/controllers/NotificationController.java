package com.tripbudget.tripbudget_core.notification.controllers;

import com.tripbudget.tripbudget_core.common.annotations.CurrentUser;
import com.tripbudget.tripbudget_core.common.dtos.*;
import com.tripbudget.tripbudget_core.common.services.HashidsService;
import com.tripbudget.tripbudget_core.notification.dtos.NotificationResponse;
import com.tripbudget.tripbudget_core.notification.services.NotificationService;
import com.tripbudget.tripbudget_core.trip.dtos.response.PageResponse;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Validated
public class NotificationController {
    private final NotificationService service;
    private final HashidsService ids;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> list(@CurrentUser CurrentUserDto user,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        Long currentUserId = user.id();
        return ok(service.list(currentUserId, page, size, unreadOnly));
    }

    @GetMapping("/snapshot")
    public ResponseEntity<ApiResponse<NotificationService.Snapshot>> snapshot(@CurrentUser CurrentUserDto user,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        Long currentUserId = user.id();
        return ok(service.snapshot(currentUserId, page, size, unreadOnly));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> count(@CurrentUser CurrentUserDto user) {
        Long currentUserId = user.id();
        return ok(service.unreadCount(currentUserId));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> read(@CurrentUser CurrentUserDto user, @PathVariable String id) {
        Long currentUserId = user.id();
        service.read(currentUserId, ids.decode(id));
        return ok(null);
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> readAll(@CurrentUser CurrentUserDto user) {
        Long currentUserId = user.id();
        service.read(currentUserId, null);
        return ok(null);
    }

    @GetMapping("/{id}/target")
    public ResponseEntity<ApiResponse<NotificationService.Target>> target(@CurrentUser CurrentUserDto user,
            @PathVariable String id) {
        Long currentUserId = user.id();
        return ok(service.target(currentUserId, ids.decode(id)));
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(HttpStatus.OK, "Success", data));
    }
}
