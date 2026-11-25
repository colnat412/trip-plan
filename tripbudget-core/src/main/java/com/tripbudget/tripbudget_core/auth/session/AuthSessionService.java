package com.tripbudget.tripbudget_core.auth.session;

import com.tripbudget.tripbudget_core.auth.config.AuthSettings;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.dao.DataIntegrityViolationException;
import com.tripbudget.tripbudget_core.auth.services.SessionHistoryService;
import com.tripbudget.tripbudget_core.auth.dtos.response.LoginSessionPage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthSessionService {
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AuthSettings settings;
    private final SessionHistoryService history;
    private final SessionClientContext clientContext;
    private final SecureRandom random = new SecureRandom();
    private static final DefaultRedisScript<Long> ROTATE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
            redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
            return 1
            """, Long.class);

    public SessionTokens create(Long userId) {
        String sessionId = UUID.randomUUID().toString();
        String secret = newRefreshSecret();
        Instant createdAt = Instant.now();
        SessionData session = new SessionData(userId.toString(), hashRefreshSecret(secret),
                createdAt, createdAt.plusSeconds(AuthSettings.REFRESH_TTL_SECONDS));
        String accessToken = signAccessToken(session.userId(), sessionId);
        try {
            history.record(userId, sessionId, session.createdAt(), session.expiresAt(), clientContext.current());
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SESSION_HISTORY_UNAVAILABLE");
        }
        try {
            long remaining = Duration.between(Instant.now(), session.expiresAt()).getSeconds();
            if (remaining <= 0) throw new IllegalStateException("Session initialization expired");
            save(sessionId, session, remaining);
        } catch (RuntimeException exception) {
            try { deleteSession(session.userId(), sessionId); }
            catch (RuntimeException cleanupFailure) { log.warn("Failed login Redis cleanup unavailable"); }
            recordEnd(userId, sessionId, "FAILED");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SESSION_STORE_UNAVAILABLE");
        }
        return new SessionTokens(accessToken, sessionId + "." + secret);
    }

    public SessionTokens refresh(String refreshToken) {
        String[] parts = refreshToken.split("\\.", -1);
        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            throw unauthorized("Invalid refresh token");
        }
        String sessionId = parts[0];
        try { UUID.fromString(sessionId); }
        catch (IllegalArgumentException exception) { throw unauthorized("Invalid refresh token"); }
        String rawSession = redis.opsForValue().get(sessionKey(sessionId));
        if (rawSession == null) {
            throw unauthorized("Session expired or revoked");
        }
        SessionData session = objectMapper.readValue(rawSession, SessionData.class);
        if (!MessageDigest.isEqual(hashRefreshSecret(parts[1]).getBytes(StandardCharsets.UTF_8),
                session.refreshTokenHash().getBytes(StandardCharsets.UTF_8))) {
            revoke(session.userId(), sessionId);
            throw unauthorized("Invalid refresh token");
        }
        long remainingSeconds = Duration.between(Instant.now(), session.expiresAt()).getSeconds();
        if (remainingSeconds <= 0) {
            importBeforeEnding(Long.valueOf(session.userId()), sessionId, session);
            deleteSession(session.userId(), sessionId);
            throw unauthorized("Session expired");
        }
        String secret = newRefreshSecret();
        SessionData rotated = new SessionData(session.userId(), hashRefreshSecret(secret),
                session.createdAt(), session.expiresAt());
        importSession(Long.valueOf(session.userId()), new SessionView(sessionId, session.createdAt(), session.expiresAt()));
        remainingSeconds = Duration.between(Instant.now(), session.expiresAt()).getSeconds();
        if (remainingSeconds <= 0) throw unauthorized("Session expired");
        Long changed = redis.execute(ROTATE, java.util.List.of(sessionKey(sessionId)),
                rawSession, objectMapper.writeValueAsString(rotated), Long.toString(remainingSeconds));
        if (!Long.valueOf(1).equals(changed)) throw unauthorized("Session changed or revoked");
        try { history.refreshed(Long.valueOf(session.userId()), sessionId); }
        catch (RuntimeException exception) { log.warn("Session refresh history update unavailable"); }
        return new SessionTokens(signAccessToken(session.userId(), sessionId), sessionId + "." + secret);
    }

    public void revoke(String userId, String sessionId) {
        SessionData session = ownedSession(Long.valueOf(userId), sessionId);
        if (session == null) return;
        importBeforeEnding(Long.valueOf(userId), sessionId, session);
        deleteSession(userId, sessionId);
        recordEnd(Long.valueOf(userId), sessionId, "REVOKED");
    }

    public void logout(Long userId, String sessionId) {
        // Capture a legacy active session before it disappears from Redis.
        SessionData session = ownedSession(userId, sessionId);
        if (session != null) importBeforeEnding(userId, sessionId, session);
        deleteSession(userId.toString(), sessionId);
        recordEnd(userId, sessionId, "LOGGED_OUT");
    }

    private void recordEnd(Long userId, String sessionId, String status) {
        try { history.ended(userId, sessionId, status); }
        catch (RuntimeException exception) { log.warn("Session ended; history update unavailable"); }
    }

    private void importBeforeEnding(Long userId, String sessionId, SessionData session) {
        try { importSession(userId, new SessionView(sessionId, session.createdAt(), session.expiresAt())); }
        catch (RuntimeException exception) { log.warn("Legacy session history import unavailable; revocation proceeds"); }
    }

    private void deleteSession(String userId, String sessionId) {
        redis.execute(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public Object execute(RedisOperations operations) {
                operations.multi();
                operations.delete(sessionKey(sessionId));
                operations.opsForSet().remove(userSessionsKey(userId), sessionId);
                return operations.exec();
            }
        });
    }

    private void save(String sessionId, SessionData session, long ttlSeconds) {
        String json = objectMapper.writeValueAsString(session);
        redis.execute(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public Object execute(RedisOperations operations) {
                operations.multi();
                operations.opsForValue().set(sessionKey(sessionId), json, Duration.ofSeconds(ttlSeconds));
                operations.opsForSet().add(userSessionsKey(session.userId()), sessionId);
                return operations.exec();
            }
        });
    }

    public record SessionView(String id, Instant createdAt, Instant expiresAt) {}

    public LoginSessionPage history(Long userId, String currentSession, int page, int size) {
        if (userId == null || userId <= 0) throw unauthorized("Unauthorized");
        if (page < 0 || size < 1 || size > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_PAGE");
        var active = listUserSessions(userId);
        active.forEach(session -> importSession(userId, session));
        var ids = active.stream().map(SessionView::id).collect(java.util.stream.Collectors.toSet());
        return history.page(userId, currentSession, page, size, ids);
    }

    private void importSession(Long userId, SessionView session) {
        try { history.importExisting(userId, session.id(), session.createdAt(), session.expiresAt()); }
        catch (DataIntegrityViolationException exception) {
            if (!history.exists(session.id())) throw exception; // Another request may have imported the same UUID.
        }
    }

    public java.util.List<SessionView> listUserSessions(Long userId) {
        var ids = redis.opsForSet().members(userSessionsKey(userId.toString()));
        if (ids == null) return java.util.List.of();
        if (ids.size() > 1000) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SESSION_LIST_LIMIT");
        var result = new java.util.ArrayList<SessionView>();
        for (String id : ids) {
            SessionData session = ownedSession(userId, id);
            if (session != null && session.createdAt() != null && session.expiresAt() != null && session.expiresAt().isAfter(Instant.now()))
                result.add(new SessionView(id, session.createdAt(), session.expiresAt()));
        }
        result.sort(java.util.Comparator.comparing(SessionView::createdAt).reversed());
        return java.util.List.copyOf(result);
    }

    public boolean revokeOwned(Long userId, String sessionId) {
        try { UUID.fromString(sessionId); }
        catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_SESSION_ID");
        }
        SessionData session = ownedSession(userId, sessionId);
        if (session == null) return false;
        revoke(userId.toString(), sessionId);
        return true;
    }

    /** Account deletion erases SQL history in its own transaction; do not import legacy history. */
    public void revokeForAccountDeletion(Long userId, String sessionId) {
        if (ownedSession(userId, sessionId) != null) deleteSession(userId.toString(), sessionId);
    }

    public void revokeForPasswordReset(Long userId, String sessionId) {
        if (ownedSession(userId, sessionId) == null) return;
        deleteSession(userId.toString(), sessionId);
        recordEnd(userId, sessionId, "REVOKED");
    }

    private SessionData ownedSession(Long userId, String sessionId) {
        String raw = redis.opsForValue().get(sessionKey(sessionId));
        if (raw == null) return null;
        SessionData session;
        try { session = objectMapper.readValue(raw, SessionData.class); }
        catch (RuntimeException exception) { return null; }
        return userId.toString().equals(session.userId()) ? session : null;
    }

    private String signAccessToken(String userId, String sessionId) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId)
                .claim("sid", sessionId)
                .claim("type", "access")
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(AuthSettings.ACCESS_TTL_SECONDS)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256)
                .type(JOSEObjectType.JWT).build(), claims);
        try {
            jwt.sign(new MACSigner(settings.getJwtSecret().getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Unable to sign access token");
        }
    }

    String hashRefreshSecret(String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(settings.getRefreshPepper().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to hash refresh token");
        }
    }

    private String newRefreshSecret() {
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sessionKey(String sessionId) {
        return "auth:session:" + sessionId;
    }

    private String userSessionsKey(String userId) {
        return "auth:user-sessions:" + userId;
    }

    private ResponseStatusException unauthorized(String message) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }
}
