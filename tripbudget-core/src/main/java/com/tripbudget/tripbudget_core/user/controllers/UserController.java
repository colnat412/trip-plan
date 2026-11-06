package com.tripbudget.tripbudget_core.user.controllers;

import com.tripbudget.tripbudget_core.common.annotations.CurrentUser;
import com.tripbudget.tripbudget_core.common.dtos.ApiResponse;
import com.tripbudget.tripbudget_core.common.dtos.CurrentUserDto;
import com.tripbudget.tripbudget_core.user.dtos.request.AcceptPoliciesRequest;
import com.tripbudget.tripbudget_core.user.dtos.request.UpdateAvatarRequest;
import com.tripbudget.tripbudget_core.user.dtos.request.UpdateProfileRequest;
import com.tripbudget.tripbudget_core.user.dtos.response.UserProfileResponse;
import com.tripbudget.tripbudget_core.user.services.UserAvatarService;
import com.tripbudget.tripbudget_core.user.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
public class UserController {
    private final UserService users;
    private final UserAvatarService avatars;

    @PostMapping("/policies/accept")
    public ResponseEntity<ApiResponse<UserProfileResponse>> acceptPolicies(
            @CurrentUser CurrentUserDto user, @RequestBody AcceptPoliciesRequest request
    ) {
        Long currentUserId = user.id();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                HttpStatus.CREATED, "Success", users.acceptPolicies(currentUserId, request)));
    }

    @PatchMapping("/profile")
    public ApiResponse<UserProfileResponse> updateProfile(
            @CurrentUser CurrentUserDto user, @Valid @RequestBody UpdateProfileRequest request
    ) {
        Long currentUserId = user.id();
        return ApiResponse.success(HttpStatus.OK, "Success", users.updateProfile(currentUserId, request.name()));
    }

    @PatchMapping("/avatar")
    public ApiResponse<UserProfileResponse> updateAvatar(
            @CurrentUser CurrentUserDto user, @Valid @RequestBody UpdateAvatarRequest request
    ) {
        Long currentUserId = user.id();
        return ApiResponse.success(HttpStatus.OK, "Success", avatars.setAvatar(currentUserId, UUID.fromString(request.uploadId())));
    }

    @DeleteMapping("/avatar")
    public ApiResponse<UserProfileResponse> removeAvatar(@CurrentUser CurrentUserDto user) {
        Long currentUserId = user.id();
        return ApiResponse.success(HttpStatus.OK, "Success", avatars.setAvatar(currentUserId, null));
    }
}
