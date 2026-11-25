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
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.tripbudget.tripbudget_core.auth.dtos.response.LoginSessionPage;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final com.tripbudget.tripbudget_core.notification.services.NotificationService notifications;
    private final AuthService auth;
    private final AuthSessionService sessions;
    private final UserService users;
    private final AuthSettings settings;
    private final com.tripbudget.tripbudget_core.auth.services.PasswordResetService passwordReset;

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordReset.request(request.email());
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(HttpStatus.OK, "If the account is eligible, a reset code will be sent.", null));
    }

    public record ResetCodeResult(String resetToken) {
        @Override public String toString() { return "ResetCodeResult[REDACTED]"; }
    }

    @PostMapping("/verify-reset-code")
    public ResponseEntity<ApiResponse<ResetCodeResult>> verifyResetCode(@Valid @RequestBody VerifyResetCodeRequest request) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(HttpStatus.OK, "Code verified", new ResetCodeResult(passwordReset.verify(request.email(), request.otp()))));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordReset.reset(request);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(HttpStatus.OK, "Password reset. Please sign in again.", null));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<OtpResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "Success", auth.register(request)));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<OtpResponse>> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "Success", auth.resendOtp(request)));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
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
            sessions.logout(currentUserId, user.sessionId());
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session");
        } finally {
            notifications.revokeSession(currentUserId, user.sessionId());
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

    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<LoginSessionPage>> mySessions(
            @CurrentUser CurrentUserDto user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long currentUserId = user.id();
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(ApiResponse.success(HttpStatus.OK, "Sessions", sessions.history(currentUserId, user.sessionId(), page, size)));
    }

    @DeleteMapping("/sessions/{sid}")
    public ResponseEntity<ApiResponse<Void>> revokeMySession(@CurrentUser CurrentUserDto user, @PathVariable String sid) {
        Long currentUserId = user.id();
        if (!sessions.revokeOwned(currentUserId, sid)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND");
        notifications.revokeSession(currentUserId, sid);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(ApiResponse.success(HttpStatus.OK, "Session revoked", null));
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
