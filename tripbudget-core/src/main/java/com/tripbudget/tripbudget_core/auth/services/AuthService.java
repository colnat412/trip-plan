package com.tripbudget.tripbudget_core.auth.services;

import com.tripbudget.tripbudget_core.auth.clients.GoogleCredentialClient;
import com.tripbudget.tripbudget_core.auth.clients.GoogleProfile;
import com.tripbudget.tripbudget_core.auth.dtos.request.*;
import com.tripbudget.tripbudget_core.auth.dtos.response.AuthResponse;
import com.tripbudget.tripbudget_core.auth.dtos.response.OtpResponse;
import com.tripbudget.tripbudget_core.auth.mail.OtpMailService;
import com.tripbudget.tripbudget_core.auth.session.AuthSessionService;
import com.tripbudget.tripbudget_core.auth.session.SessionTokens;
import com.tripbudget.tripbudget_core.auth.session.LoginResult;
import com.tripbudget.tripbudget_core.user.entities.UserEntity;
import com.tripbudget.tripbudget_core.user.repositories.UserRepository;
import com.tripbudget.tripbudget_core.user.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import com.tripbudget.tripbudget_core.auth.password.PasswordHasher;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository users;
    private final UserService profiles;
    private final StringRedisTemplate redis;
    private final AuthSessionService sessions;
    private final OtpMailService mailer;
    private final GoogleCredentialClient google;
    private final PasswordHasher passwords;
    private final SecureRandom random = new SecureRandom();

    public OtpResponse register(RegisterRequest request) {
        String email = normalizedEmail(request.email());
        UserEntity user = users.findByEmail(email).orElse(null);
        if (user != null && !"INACTIVE".equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }
        String passwordHash = passwords.encode(request.password());
        if (user == null) {
            user = UserEntity.register(email, request.name().trim(), passwordHash);
        } else {
            user.updateRegistration(request.name().trim(), passwordHash);
        }
        user = users.saveAndFlush(user);
        sendOtp(user);
        return new OtpResponse(true, user.getEmail(), "OTP has been sent to your email.");
    }

    public LoginResult login(LoginRequest request) {
        try {
            UserEntity user = users.findByEmail(normalizedEmail(request.email())).orElseThrow();
            if (user.getPassword() == null || !passwords.matches(request.password(), user.getPassword())
                    || !"ACTIVE".equals(user.getStatus())) {
                throw new IllegalArgumentException("Invalid credentials");
            }
            return signIn(user);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().is5xxServerError()) throw exception;
            throw unauthorized("Email or password is not valid");
        } catch (RuntimeException exception) {
            throw unauthorized("Email or password is not valid");
        }
    }

    public LoginResult verifyOtp(VerifyOtpRequest request) {
        try {
            String email = normalizedEmail(request.email());
            String otp = redis.opsForValue().get(otpKey(email));
            if (otp == null || !otp.equals(request.otp())) throw new IllegalArgumentException("Invalid OTP");
            UserEntity user = users.findByEmail(email).orElseThrow();
            if (!"INACTIVE".equals(user.getStatus())) throw new IllegalArgumentException("Account cannot be verified");
            user.activate();
            users.saveAndFlush(user);
            redis.delete(otpKey(email));
            return signIn(user);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().is5xxServerError()) throw exception;
            throw unauthorized("Invalid OTP");
        } catch (RuntimeException exception) {
            throw unauthorized("Invalid OTP");
        }
    }

    public OtpResponse resendOtp(ResendOtpRequest request) {
        try {
            UserEntity user = users.findByEmail(normalizedEmail(request.email())).orElseThrow();
            if (!"INACTIVE".equals(user.getStatus())) throw new IllegalArgumentException("Account cannot receive an OTP");
            sendOtp(user);
            return new OtpResponse(true, user.getEmail(), "OTP has been resent to your email.");
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unauthorized("Failed to resend OTP");
        }
    }

    public LoginResult loginWithGoogle(GoogleLoginRequest request) {
        try {
            GoogleProfile profile = google.verify(request.credential());
            if (profile.email() == null || profile.email().isEmpty()) throw new IllegalArgumentException("Missing email");
            String email = profile.email().toLowerCase(Locale.ROOT);
            UserEntity user = users.findByEmail(email).orElseGet(() -> UserEntity.fromGoogle(email,
                    profile.name() == null || profile.name().isEmpty() ? email.split("@")[0] : profile.name(), profile.picture()));
            if (java.util.Set.of("BLOCKED","DELETED").contains(user.getStatus())) throw unauthorized("Account disabled");
            user.updateGoogleProfile(profile.picture());
            user = users.saveAndFlush(user);
            return signIn(user);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().is5xxServerError()) throw exception;
            throw unauthorized("Failed to login with Google");
        } catch (RuntimeException exception) {
            throw unauthorized("Failed to login with Google");
        }
    }

    private LoginResult signIn(UserEntity user) {
        SessionTokens tokens = sessions.create(user.getId());
        return new LoginResult(new AuthResponse(tokens.accessToken(), profiles.profile(user)), tokens.refreshToken());
    }

    private void sendOtp(UserEntity user) {
        String otp = Integer.toString(100000 + random.nextInt(900000));
        redis.opsForValue().set(otpKey(user.getEmail()), otp, Duration.ofMinutes(5));
        if (!mailer.sendOtp(user.getEmail(), otp, user.getName())) {
            redis.delete(otpKey(user.getEmail()));
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to send verification email. Please try again later.");
        }
    }

    private String otpKey(String email) {
        return "otp:verify:" + email;
    }

    private String normalizedEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private ResponseStatusException unauthorized(String message) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }
}
