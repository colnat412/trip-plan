package com.tripbudget.tripbudget_core.user;

import com.tripbudget.tripbudget_core.auth.clients.GoogleCredentialClient;
import com.tripbudget.tripbudget_core.auth.dtos.request.LoginRequest;
import com.tripbudget.tripbudget_core.auth.mail.OtpMailService;
import com.tripbudget.tripbudget_core.auth.password.PasswordHasher;
import com.tripbudget.tripbudget_core.auth.services.AuthService;
import com.tripbudget.tripbudget_core.auth.session.*;
import com.tripbudget.tripbudget_core.common.services.HashidsService;
import com.tripbudget.tripbudget_core.user.dtos.request.UpdatePasswordRequest;
import com.tripbudget.tripbudget_core.user.entities.UserEntity;
import com.tripbudget.tripbudget_core.user.repositories.UserRepository;
import com.tripbudget.tripbudget_core.user.services.UserService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserPasswordTest {
    private final UserRepository users = mock(UserRepository.class);
    private final HashidsService hashids = mock(HashidsService.class);
    private final PasswordHasher passwords = mock(PasswordHasher.class);
    private final UserService service = new UserService(users, hashids, passwords);
    private UserEntity user;

    @BeforeEach
    void setup() {
        user = UserEntity.fromGoogle("member@example.com", "Member", null);
        ReflectionTestUtils.setField(user, "id", 42L);
        when(users.findForUpdateById(42L)).thenReturn(Optional.of(user));
        when(users.save(any())).thenAnswer(call -> call.getArgument(0));
        when(hashids.encode(42L)).thenReturn("encoded-id");
        when(passwords.encode(anyString())).thenReturn("new-hash");
    }

    @Test
    void googleAccountSetsFirstPasswordOnSameAccountWithoutOldPassword() {
        assertFalse(service.profile(user).hasPassword());
        var profile = service.updatePassword(42L, new UpdatePasswordRequest(null, "newPassword123"));
        assertTrue(profile.hasPassword());
        assertEquals("new-hash", user.getPassword());
        assertEquals("encoded-id", profile.id());
        assertEquals("member@example.com", profile.email());
        assertEquals("Member", profile.name());
        assertEquals("ACTIVE", user.getStatus());
        verify(passwords).encode("newPassword123");
        verify(passwords, never()).matches(any(), any());
        verify(users).findForUpdateById(42L);
    }

    @Test
    void existingPasswordRequiresCorrectCurrentPassword() {
        user.updatePassword("old-hash");
        when(passwords.matches("oldPassword123", "old-hash")).thenReturn(true);
        var profile = service.updatePassword(42L, new UpdatePasswordRequest("oldPassword123", "newPassword123"));
        assertTrue(profile.hasPassword());
        verify(passwords).matches("oldPassword123", "old-hash");
        assertEquals("new-hash", user.getPassword());
    }

    @Test
    void wrongOrMissingCurrentPasswordCannotOverwriteExistingPassword() {
        user.updatePassword("old-hash");
        for (String current : new String[] {null, "", "wrong"}) {
            assertEquals(422, assertThrows(ResponseStatusException.class,
                    () -> service.updatePassword(42L, new UpdatePasswordRequest(current, "newPassword123"))).getStatusCode().value());
        }
        assertEquals("old-hash", user.getPassword());
        verify(passwords, never()).encode(any());
        verify(users, never()).save(any());
    }

    @Test
    void staleFirstSetupCannotOverwritePasswordAfterItWasSet() {
        service.updatePassword(42L, new UpdatePasswordRequest(null, "firstPassword123"));
        assertEquals(422, assertThrows(ResponseStatusException.class,
                () -> service.updatePassword(42L, new UpdatePasswordRequest(null, "secondPassword123"))).getStatusCode().value());
        verify(users, times(1)).save(any());
    }

    @Test
    void unchangedPasswordIsRejected() {
        user.updatePassword("old-hash");
        when(passwords.matches("samePassword123", "old-hash")).thenReturn(true);
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.updatePassword(42L, new UpdatePasswordRequest("samePassword123", "samePassword123"))).getStatusCode().value());
        verify(users, never()).save(any());
    }

    @Test
    void invalidOrTruncatedBcryptPasswordsAreRejected() {
        for (String value : new String[] {null, "", "        ", "short", "a".repeat(73), "ế".repeat(25)}) {
            assertEquals(400, assertThrows(ResponseStatusException.class,
                    () -> service.updatePassword(42L, new UpdatePasswordRequest(null, value))).getStatusCode().value());
        }
        verify(passwords, never()).encode(any());
        verify(users, never()).save(any());
    }

    @Test
    void inactiveAndBlockedAccountsAreRejected() {
        for (String status : new String[] {"INACTIVE", "BLOCKED"}) {
            ReflectionTestUtils.setField(user, "status", status);
            assertEquals(403, assertThrows(ResponseStatusException.class,
                    () -> service.updatePassword(42L, new UpdatePasswordRequest(null, "newPassword123"))).getStatusCode().value());
        }
        verify(users, never()).save(any());
    }

    @Test
    void missingAccountIsRejectedAndRequestDoesNotLogCredentials() {
        when(users.findForUpdateById(42L)).thenReturn(Optional.empty());
        var request = new UpdatePasswordRequest("oldSecret", "newSecret123");
        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> service.updatePassword(42L, request)).getStatusCode().value());
        assertFalse(request.toString().contains("Secret"));
    }

    @Test
    void requestValidationEnforcesMinimumLength() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertFalse(validator.validate(new UpdatePasswordRequest(null, "short")).isEmpty());
            assertFalse(validator.validate(new UpdatePasswordRequest(null, "a".repeat(73))).isEmpty());
            assertTrue(validator.validate(new UpdatePasswordRequest(null, "validPassword123")).isEmpty());
        }
    }

    @Test
    void googleUserCanLoginByEmailAfterSettingPasswordAndOldPasswordStopsWorkingAfterChange() {
        var realPasswords = new PasswordHasher();
        var realService = new UserService(users, hashids, realPasswords);
        when(users.findByEmail("member@example.com")).thenReturn(Optional.of(user));
        var sessions = mock(AuthSessionService.class);
        when(sessions.create(42L)).thenReturn(new SessionTokens("access", "refresh"));
        var auth = new AuthService(users, realService, mock(StringRedisTemplate.class), sessions,
                mock(OtpMailService.class), mock(GoogleCredentialClient.class), realPasswords);
        realService.updatePassword(42L, new UpdatePasswordRequest(null, "firstPassword123"));
        assertNotEquals("firstPassword123", user.getPassword());
        assertEquals("access", auth.login(new LoginRequest("member@example.com", "firstPassword123")).response().accessToken());
        realService.updatePassword(42L, new UpdatePasswordRequest("firstPassword123", "secondPassword123"));
        assertThrows(ResponseStatusException.class, () -> auth.login(new LoginRequest("member@example.com", "firstPassword123")));
        assertEquals("access", auth.login(new LoginRequest("member@example.com", "secondPassword123")).response().accessToken());
    }
}
