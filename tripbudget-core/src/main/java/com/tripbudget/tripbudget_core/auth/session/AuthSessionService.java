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
public class AuthSessionService {
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AuthSettings settings;
    private final SecureRandom random = new SecureRandom();

    public SessionTokens create(Long userId) {
        String sessionId = UUID.randomUUID().toString();
        String secret = newRefreshSecret();
        Instant createdAt = Instant.now();
        SessionData session = new SessionData(userId.toString(), hashRefreshSecret(secret),
                createdAt, createdAt.plusSeconds(AuthSettings.REFRESH_TTL_SECONDS));
        save(sessionId, session, AuthSettings.REFRESH_TTL_SECONDS);
        return new SessionTokens(signAccessToken(session.userId(), sessionId), sessionId + "." + secret);
    }

    public SessionTokens refresh(String refreshToken) {
        String[] parts = refreshToken.split("\\.", -1);
        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            throw unauthorized("Invalid refresh token");
        }
        String sessionId = parts[0];
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
            revoke(session.userId(), sessionId);
            throw unauthorized("Session expired");
        }
        String secret = newRefreshSecret();
        SessionData rotated = new SessionData(session.userId(), hashRefreshSecret(secret),
                session.createdAt(), session.expiresAt());
        save(sessionId, rotated, remainingSeconds);
        return new SessionTokens(signAccessToken(session.userId(), sessionId), sessionId + "." + secret);
    }

    public void revoke(String userId, String sessionId) {
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
