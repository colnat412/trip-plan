package com.tripbudget.tripbudget_core.admin.controllers;

import com.tripbudget.tripbudget_core.admin.dtos.AdminDtos.*;
import com.tripbudget.tripbudget_core.admin.services.AdminService;
import com.tripbudget.tripbudget_core.common.annotations.CurrentUser;
import com.tripbudget.tripbudget_core.common.dtos.*;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService admin;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<User>> me(@CurrentUser CurrentUserDto user) {
        Long actorId = user.id();
        return success(admin.me(actorId));
    }

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<Overview>> overview(@CurrentUser CurrentUserDto user) {
        Long actorId = user.id();
        return success(admin.overview(actorId));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<PageResult<User>>> users(
            @CurrentUser CurrentUserDto user,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long actorId = user.id();
        return success(admin.users(actorId, q, status, page, size));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<ApiResponse<User>> user(
            @CurrentUser CurrentUserDto user, @PathVariable String id) {
        Long actorId = user.id();
        return success(admin.user(actorId, id));
    }

    @PatchMapping("/users/{id}")
    public ResponseEntity<ApiResponse<User>> updateUser(
            @CurrentUser CurrentUserDto user,
            @PathVariable String id,
            @Valid @RequestBody UpdateUser body) {
        Long actorId = user.id();
        return success(admin.updateUser(actorId, id, body));
    }

    @PatchMapping("/users/{id}/status")
    public ResponseEntity<ApiResponse<User>> status(
            @CurrentUser CurrentUserDto user,
            @PathVariable String id,
            @Valid @RequestBody ChangeStatus body) {
        Long actorId = user.id();
        return success(admin.changeStatus(actorId, id, body));
    }

    @GetMapping("/users/{id}/sessions")
    public ResponseEntity<ApiResponse<Sessions>> sessions(
            @CurrentUser CurrentUserDto user, @PathVariable String id) {
        Long actorId = user.id();
        return success(admin.sessions(actorId, user.sessionId(), id));
    }

    @PostMapping("/users/{id}/sessions/revoke")
    public ResponseEntity<ApiResponse<Revocation>> revokeAll(
            @CurrentUser CurrentUserDto user,
            @PathVariable String id,
            @Valid @RequestBody Reason body) {
        Long actorId = user.id();
        return success(admin.revokeAll(actorId, id, body));
    }

    @PostMapping("/users/{id}/sessions/{sid}/revoke")
    public ResponseEntity<ApiResponse<Revocation>> revoke(
            @CurrentUser CurrentUserDto user,
            @PathVariable String id,
            @PathVariable String sid,
            @Valid @RequestBody Reason body) {
        Long actorId = user.id();
        return success(admin.revoke(actorId, id, sid, body));
    }

    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<Settings>> settings(@CurrentUser CurrentUserDto user) {
        Long actorId = user.id();
        return success(admin.settings(actorId));
    }

    @PutMapping("/settings")
    public ResponseEntity<ApiResponse<Settings>> updateSettings(
            @CurrentUser CurrentUserDto user, @Valid @RequestBody UpdateSettings body) {
        Long actorId = user.id();
        return success(admin.updateSettings(actorId, body));
    }

    @GetMapping("/audit")
    public ResponseEntity<ApiResponse<PageResult<Audit>>> audit(
            @CurrentUser CurrentUserDto user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long actorId = user.id();
        return success(admin.audit(actorId, page, size));
    }

    private <T> ResponseEntity<ApiResponse<T>> success(T data) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(HttpStatus.OK, "Success", data));
    }
}
