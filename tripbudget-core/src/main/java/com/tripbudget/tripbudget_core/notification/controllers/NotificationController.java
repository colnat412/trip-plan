package com.tripbudget.tripbudget_core.notification.controllers;

import com.tripbudget.tripbudget_core.common.annotations.CurrentUser;
import com.tripbudget.tripbudget_core.common.dtos.*;
import com.tripbudget.tripbudget_core.notification.dtos.request.*;
import com.tripbudget.tripbudget_core.notification.dtos.response.*;
import com.tripbudget.tripbudget_core.notification.services.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notifications;
    private final com.tripbudget.tripbudget_core.common.services.HashidsService hashids;
    @ModelAttribute
    public void preventCaching(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }
    @GetMapping("/notifications")
    public ApiResponse<NotificationPageResponse> inbox(@CurrentUser CurrentUserDto user,
        @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int limit,
        @RequestParam(defaultValue = "false") boolean unreadOnly) {
        Long currentUserId = user.id();
        return ok(notifications.inbox(currentUserId, cursor, limit, unreadOnly));
    }
    @GetMapping("/notifications/unread-count")
    public ApiResponse<UnreadCountResponse> unread(@CurrentUser CurrentUserDto user) {
        Long currentUserId = user.id();
        return ok(notifications.unread(currentUserId));
    }
    @GetMapping("/notifications/{id}")
    public ApiResponse<NotificationResponse> detail(@CurrentUser CurrentUserDto user, @PathVariable("id") String idHash) {
        Long currentUserId = user.id();
        Long notificationId = hashids.decode(idHash);
        return ok(notifications.detail(currentUserId, notificationId));
    }
    @PostMapping("/notifications/{id}/invitation-response")
    public ApiResponse<NotificationResponse> respondToInvitation(@CurrentUser CurrentUserDto user,
        @PathVariable("id") String idHash, @Valid @RequestBody InvitationResponseRequest request) {
        Long currentUserId = user.id();
        Long notificationId = hashids.decode(idHash);
        return ok(notifications.respondToInvitation(currentUserId, notificationId, request.accept()));
    }
    @DeleteMapping("/notifications/{id}")
    public ApiResponse<Void> delete(@CurrentUser CurrentUserDto user, @PathVariable("id") String idHash) {
        Long currentUserId = user.id();
        Long notificationId = hashids.decode(idHash);
        notifications.deleteNotification(currentUserId, notificationId);
        return ok(null);
    }
    @PatchMapping("/notifications/{id}/read")
    public ApiResponse<Void> read(@CurrentUser CurrentUserDto user, @PathVariable("id") String idHash) {
        Long currentUserId = user.id();
        Long notificationId = hashids.decode(idHash);
        notifications.markRead(currentUserId, notificationId);
        return ok(null);
    }
    @PostMapping("/notifications/read-all")
    public ApiResponse<Void> readAll(@CurrentUser CurrentUserDto user, @Valid @RequestBody ReadAllRequest request) {
        Long currentUserId = user.id();
        notifications.readAll(currentUserId, request.through());
        return ok(null);
    }
    @GetMapping("/notification-preferences")
    public ApiResponse<NotificationPreferenceResponse> preferences(@CurrentUser CurrentUserDto user) {
        Long currentUserId = user.id();
        return ok(notifications.getPreferences(currentUserId));
    }
    @PutMapping("/notification-preferences")
    public ApiResponse<NotificationPreferenceResponse> preferences(@CurrentUser CurrentUserDto user,
        @Valid @RequestBody NotificationPreferenceRequest request) {
        Long currentUserId = user.id();
        return ok(notifications.updatePreferences(currentUserId, request));
    }
    @PostMapping("/push-devices")
    public ApiResponse<PushDeviceResponse> register(@CurrentUser CurrentUserDto user, @Valid @RequestBody PushDeviceRequest request) {
        Long currentUserId = user.id();
        return ok(notifications.registerDevice(currentUserId, user.sessionId(), request));
    }
    @DeleteMapping("/push-devices/{id}")
    public ApiResponse<Void> revoke(@CurrentUser CurrentUserDto user, @PathVariable("id") String idHash) {
        Long currentUserId = user.id();
        Long deviceId = hashids.decode(idHash);
        notifications.revokeDevice(currentUserId, deviceId);
        return ok(null);
    }
    private <T> ApiResponse<T> ok(T data) { return ApiResponse.success(HttpStatus.OK, "Success", data); }
}
