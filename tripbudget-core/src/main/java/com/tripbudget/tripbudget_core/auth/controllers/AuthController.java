package com.tripbudget.tripbudget_core.auth.controllers;

import com.tripbudget.tripbudget_core.auth.config.AuthSettings;
import com.tripbudget.tripbudget_core.auth.dtos.request.*;
import com.tripbudget.tripbudget_core.auth.dtos.response.AuthResponse;
import com.tripbudget.tripbudget_core.auth.dtos.response.OtpResponse;
import com.tripbudget.tripbudget_core.auth.dtos.response.RefreshResponse;
import com.tripbudget.tripbudget_core.auth.services.AuthService;
import com.tripbudget.tripbudget_core.auth.session.AuthSessionService;
import com.tripbudget.tripbudget_core.auth.session.LoginResult;
import com.tripbudget.tripbudget_core.common.annotations.CurrentUser;
import com.tripbudget.tripbudget_core.common.dtos.ApiResponse;
import com.tripbudget.tripbudget_core.common.dtos.CurrentUserDto;
import com.tripbudget.tripbudget_core.user.dtos.response.UserProfileResponse;
import com.tripbudget.tripbudget_core.user.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService auth;
    private final AuthSessionService sessions;
    private final UserService users;
    private final AuthSettings settings;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<OtpResponse>> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "Success", auth.register(request)));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<OtpResponse>> resendOtp(@RequestBody ResendOtpRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "Success", auth.resendOtp(request)));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyOtp(@RequestBody VerifyOtpRequest request) {
        return signedIn(auth.verifyOtp(request));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@RequestBody LoginRequest request) {
        return signedIn(auth.login(request));
    }

    @PostMapping("/google")
    public ResponseEntity<ApiResponse<AuthResponse>> google(@RequestBody GoogleLoginRequest request) {
        return signedIn(auth.loginWithGoogle(request));
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> me(@CurrentUser CurrentUserDto user) {
        Long currentUserId = user.id();
        return ApiResponse.success(HttpStatus.OK, "Success", users.getProfile(currentUserId));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken
    ) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is missing");
        }
        var tokens = sessions.refresh(refreshToken);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refreshToken(), settings.getCookieTtlSeconds()))
                .body(ApiResponse.success(HttpStatus.CREATED, "Success", new RefreshResponse(tokens.accessToken())));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@CurrentUser CurrentUserDto user) {
        Long currentUserId = user.id();
        try {
            sessions.revoke(currentUserId.toString(), user.sessionId());
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", 0))
                .body(ApiResponse.success(HttpStatus.CREATED, "Success", null));
    }

    @GetMapping("/test")
    public ApiResponse<String> test(@CurrentUser CurrentUserDto user) {
        Long currentUserId = user.id();
        return ApiResponse.success(HttpStatus.OK, "Success", "GET TEST GUARD");
    }

    private ResponseEntity<ApiResponse<AuthResponse>> signedIn(LoginResult result) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken(), settings.getCookieTtlSeconds()))
                .body(ApiResponse.success(HttpStatus.CREATED, "Success", result.response()));
    }

    private String refreshCookie(String value, long maxAge) {
        return ResponseCookie.from("refresh_token", value).httpOnly(true).secure(settings.isCookieSecure())
                .sameSite("Lax").path("/api/auth").maxAge(maxAge).build().toString();
    }
}
