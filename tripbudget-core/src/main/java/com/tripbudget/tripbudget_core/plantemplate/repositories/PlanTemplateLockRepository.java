package com.tripbudget.tripbudget_core.plantemplate.repositories;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PlanTemplateLockRepository {
    private static final String ACQUIRE_TRANSACTION_LOCK = "SELECT pg_advisory_xact_lock(?)";
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public void lockPublication(Long sourceTripId) {
        lockIdentity("publish:" + sourceTripId);
    }

    public void lockCopy(Long currentUserId, UUID idempotencyKey) {
        lockIdentity("copy:" + currentUserId + ":" + idempotencyKey);
    }

    private void lockIdentity(String identity) {
        long lockKey = createLockKey(identity);
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(ACQUIRE_TRANSACTION_LOCK)) {
                statement.setLong(1, lockKey);
                statement.execute();
            }
            return null;
        });
    }

    private long createLockKey(String identity) {
        try {
            String serializedIdentity = objectMapper.writeValueAsString(identity);
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(serializedIdentity.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest).getLong();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
