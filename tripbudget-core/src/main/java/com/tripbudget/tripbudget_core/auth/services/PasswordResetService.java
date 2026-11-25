package com.tripbudget.tripbudget_core.auth.services;

import com.tripbudget.tripbudget_core.auth.config.AuthSettings;
import com.tripbudget.tripbudget_core.auth.dtos.request.ResetPasswordRequest;
import com.tripbudget.tripbudget_core.auth.mail.OtpMailService;
import com.tripbudget.tripbudget_core.auth.password.PasswordHasher;
import com.tripbudget.tripbudget_core.auth.session.AuthSessionService;
import com.tripbudget.tripbudget_core.user.entities.UserEntity;
import com.tripbudget.tripbudget_core.user.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PasswordResetService {
    private final UserRepository users;
    private final StringRedisTemplate redis;
    private final PasswordHasher passwords;
    private final AuthSessionService sessions;
    private final OtpMailService mailer;
    private final AuthSettings settings;
    private final SecureRandom random = new SecureRandom();
    private static final DefaultRedisScript<String> TAKE_TICKET = new DefaultRedisScript<>("""
            local value = redis.call('GET', KEYS[1])
            if value then redis.call('DEL', KEYS[1]) end
            return value
            """, String.class);
    private static final DefaultRedisScript<Long> SEND_LIMIT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
            if tonumber(redis.call('GET', KEYS[2]) or '0') >= 5 then return 0 end
            redis.call('SET', KEYS[1], '1', 'EX', 60)
            local count = redis.call('INCR', KEYS[2])
            if count == 1 then redis.call('EXPIRE', KEYS[2], 3600) end
            return 1
            """, Long.class);
    private static final DefaultRedisScript<String> CONSUME = new DefaultRedisScript<>("""
            local data = redis.call('GET', KEYS[1])
            if not data then return nil end
            local count = redis.call('INCR', KEYS[2])
            if count == 1 then redis.call('EXPIRE', KEYS[2], 300) end
            if count > 5 then redis.call('DEL', KEYS[1]); return nil end
            if string.sub(data, -64) ~= ARGV[1] then
              if count >= 5 then redis.call('DEL', KEYS[1]) end
              return nil
            end
            redis.call('DEL', KEYS[1], KEYS[2])
            return data
            """, String.class);

    public void request(String inputEmail) {
        String email = normalize(inputEmail), key = key(email);
        if (!Long.valueOf(1).equals(redis.execute(SEND_LIMIT, List.of(key + ":cooldown", key + ":quota")))) return;
        var user = users.findByEmail(email).filter(account -> "ACTIVE".equals(account.getStatus())).orElse(null);
        if (user == null) return; // Same public response for absent, unverified or disabled accounts.
        String otp = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        String data = user.getId() + ":" + fingerprint(user) + ":" + digest(email + "\0" + otp);
        redis.opsForValue().set(key + ":code", data, Duration.ofMinutes(5));
        redis.delete(key + ":attempts");
        if (!mailer.sendPasswordReset(email, otp, user.getName())) redis.delete(key + ":code");
    }

    public String verify(String inputEmail, String otp) {
        String email = normalize(inputEmail), key = key(email);
        if (otp == null || !otp.matches("\\d{6}")) throw invalidCode();
        String accepted = redis.execute(CONSUME, List.of(key + ":code", key + ":attempts"), digest(email + "\0" + otp));
        if (accepted == null) throw invalidCode();
        var user = users.findByEmail(email).orElseThrow(PasswordResetService::invalidCode);
        if (!"ACTIVE".equals(user.getStatus()) || !accepted.equals(user.getId() + ":" + fingerprint(user) + ":" + digest(email + "\0" + otp))) throw invalidCode();
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set("auth:password-reset:ticket:" + digest(token), user.getId() + ":" + fingerprint(user), Duration.ofMinutes(10));
        return token;
    }

    @Transactional
    public void reset(ResetPasswordRequest request) {
        String password = request.newPassword();
        if (password == null || password.isBlank() || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD");
        if (request.resetToken() == null || !request.resetToken().matches("[A-Za-z0-9_-]{43}")) throw invalidCode();
        String accepted = redis.execute(TAKE_TICKET, List.of("auth:password-reset:ticket:" + digest(request.resetToken())));
        if (accepted == null) throw invalidCode();
        Long userId = Long.valueOf(accepted.split(":", 2)[0]);
        var user = users.findForUpdateById(userId).orElseThrow(PasswordResetService::invalidCode);
        if (!"ACTIVE".equals(user.getStatus()) || !accepted.equals(user.getId() + ":" + fingerprint(user))) throw invalidCode();
        // No legacy history imports under the locked account row.
        for (var session : sessions.listUserSessions(user.getId())) sessions.revokeForPasswordReset(user.getId(), session.id());
        user.updatePassword(passwords.encode(password));
        users.saveAndFlush(user);
    }

    private String fingerprint(UserEntity user) {
        return digest("credential:" + Objects.toString(user.getEmail(), "") + "\0" + Objects.toString(user.getPassword(), ""));
    }
    private String key(String email) { return "auth:password-reset:{" + digest(email) + "}"; }
    private String digest(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(settings.getJwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException error) { throw new IllegalStateException("Password reset unavailable"); }
    }
    private static String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private static ResponseStatusException invalidCode() { return new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_RESET_CODE"); }
}
