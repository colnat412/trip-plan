package com.tripbudget.tripbudget_core.auth;

import com.tripbudget.tripbudget_core.auth.clients.*;
import com.tripbudget.tripbudget_core.auth.dtos.request.*;
import com.tripbudget.tripbudget_core.auth.mail.OtpMailService;
import com.tripbudget.tripbudget_core.auth.password.PasswordHasher;
import com.tripbudget.tripbudget_core.auth.services.AuthService;
import com.tripbudget.tripbudget_core.auth.session.*;
import com.tripbudget.tripbudget_core.user.entities.UserEntity;
import com.tripbudget.tripbudget_core.user.repositories.UserRepository;
import com.tripbudget.tripbudget_core.user.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthOtpTest {
    private final UserRepository users = mock(UserRepository.class);
    private final UserService profiles = mock(UserService.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final AuthSessionService sessions = mock(AuthSessionService.class);
    private final OtpMailService mailer = mock(OtpMailService.class);
    private final GoogleCredentialClient google = mock(GoogleCredentialClient.class);
    private final PasswordHasher passwords = mock(PasswordHasher.class);
    private final AuthService auth = new AuthService(users, profiles, redis, sessions, mailer, google, passwords);
    private final String email = "member@example.com";
    private final String key = "otp:verify:" + email;
    private UserEntity pending;

    @BeforeEach
    void setup() {
        pending = UserEntity.register(email, "Member", "hashed");
        ReflectionTestUtils.setField(pending, "id", 42L);
        when(redis.opsForValue()).thenReturn(values);
        when(users.findByEmail(email)).thenReturn(Optional.of(pending));
        when(users.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(passwords.encode(anyString())).thenReturn("hashed");
        when(mailer.sendOtp(anyString(), anyString(), anyString())).thenReturn(true);
        when(sessions.create(42L)).thenReturn(new SessionTokens("access", "refresh"));
    }

    @Test
    void registrationStaysInactiveUntilOtpAndNormalizesEmail() {
        when(users.findByEmail(email)).thenReturn(Optional.empty());
        auth.register(new RegisterRequest(" Member@Example.com ", "password123", " Member "));
        var captured = org.mockito.ArgumentCaptor.forClass(UserEntity.class);
        verify(users).saveAndFlush(captured.capture());
        assertEquals("INACTIVE", captured.getValue().getStatus());
        assertEquals(email, captured.getValue().getEmail());
        assertEquals("Member", captured.getValue().getName());
        assertEquals("hashed", captured.getValue().getPassword());
        verify(values).set(eq(key), matches("[0-9]{6}"), eq(Duration.ofMinutes(5)));
        verify(mailer).sendOtp(eq(email), matches("[0-9]{6}"), eq("Member"));
        verify(sessions, never()).create(anyLong());
    }

    @Test
    void wrongOrExpiredOtpCannotActivate() {
        for (String stored : new String[] {null, "123456"}) {
            when(values.get(key)).thenReturn(stored);
            assertEquals(401, assertThrows(ResponseStatusException.class,
                    () -> auth.verifyOtp(new VerifyOtpRequest(email, "654321"))).getStatusCode().value());
        }
        assertEquals("INACTIVE", pending.getStatus());
        verify(users, never()).saveAndFlush(any());
        verify(sessions, never()).create(anyLong());
    }

    @Test
    void validOtpActivatesConsumesCodeAndCreatesSession() {
        when(values.get(key)).thenReturn("123456");
        var result = auth.verifyOtp(new VerifyOtpRequest(email, "123456"));
        assertEquals("ACTIVE", pending.getStatus());
        verify(users).saveAndFlush(pending);
        verify(redis).delete(key);
        assertEquals("access", result.response().accessToken());
        assertEquals("refresh", result.refreshToken());
    }

    @Test
    void resendRenewsCodeWithoutActivation() {
        auth.resendOtp(new ResendOtpRequest(email));
        verify(values).set(eq(key), matches("[0-9]{6}"), eq(Duration.ofMinutes(5)));
        assertEquals("INACTIVE", pending.getStatus());
        verify(sessions, never()).create(anyLong());
    }

    @Test
    void mailFailureReturns503DeletesUndeliveredCodeAndKeepsAccountPending() {
        when(mailer.sendOtp(anyString(), anyString(), anyString())).thenReturn(false);
        assertEquals(503, assertThrows(ResponseStatusException.class,
                () -> auth.register(new RegisterRequest(email, "password123", "Member"))).getStatusCode().value());
        assertEquals(503, assertThrows(ResponseStatusException.class,
                () -> auth.resendOtp(new ResendOtpRequest(email))).getStatusCode().value());
        verify(redis, times(2)).delete(key);
        assertEquals("INACTIVE", pending.getStatus());
        verify(sessions, never()).create(anyLong());
    }

    @Test
    void activeOrBlockedAccountsCannotBeOverwrittenByRegistrationOrOtp() {
        for (String status : new String[] {"ACTIVE", "BLOCKED"}) {
            ReflectionTestUtils.setField(pending, "status", status);
            when(values.get(key)).thenReturn("123456");
            assertEquals(409, assertThrows(ResponseStatusException.class,
                    () -> auth.register(new RegisterRequest(email, "password123", "Other"))).getStatusCode().value());
            assertThrows(ResponseStatusException.class, () -> auth.resendOtp(new ResendOtpRequest(email)));
            assertThrows(ResponseStatusException.class, () -> auth.verifyOtp(new VerifyOtpRequest(email, "123456")));
            assertEquals(status, pending.getStatus());
        }
        verify(users, never()).saveAndFlush(any());
        verify(sessions, never()).create(anyLong());
    }

    @Test
    void googleUsesExistingManualAccountAndIdForSameEmail() {
        when(google.verify("google-token")).thenReturn(new GoogleProfile("Member@Example.com", "Google name", null));
        auth.loginWithGoogle(new GoogleLoginRequest("google-token"));
        verify(users).saveAndFlush(pending);
        verify(sessions).create(42L);
        assertEquals(42L, pending.getId());
        assertEquals("hashed", pending.getPassword());
        assertEquals("Member", pending.getName());
        assertEquals("ACTIVE", pending.getStatus());
        verify(mailer, never()).sendOtp(anyString(), anyString(), anyString());
    }
}
