package com.tripbudget.tripbudget_core.auth.repositories;

import com.tripbudget.tripbudget_core.auth.entities.LoginSessionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LoginSessionRepository extends JpaRepository<LoginSessionEntity, Long> {
    boolean existsBySessionId(UUID sessionId);
    @Query(value = """
            SELECT session.* FROM trip_core.login_sessions session
            WHERE session.is_del = false AND (:query = '' OR EXISTS (
                SELECT account.id FROM trip_core.users account WHERE account.id = session.user_id
                AND (LOWER(account.name) LIKE LOWER(:query) ESCAPE '!'
                     OR LOWER(account.email) LIKE LOWER(:query) ESCAPE '!')))
            ORDER BY session.signed_in_at DESC, session.id DESC
            """, countQuery = """
            SELECT COUNT(*) FROM trip_core.login_sessions session
            WHERE session.is_del = false AND (:query = '' OR EXISTS (
                SELECT account.id FROM trip_core.users account WHERE account.id = session.user_id
                AND (LOWER(account.name) LIKE LOWER(:query) ESCAPE '!'
                     OR LOWER(account.email) LIKE LOWER(:query) ESCAPE '!')))
            """, nativeQuery = true)
    Page<LoginSessionEntity> searchForAdmin(@Param("query") String query, Pageable pageable);
    Page<LoginSessionEntity> findByUserIdAndIsDelFalseOrderBySignedInAtDescIdDesc(Long userId, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LoginSessionEntity> findBySessionIdAndUserIdAndIsDelFalse(UUID sessionId, Long userId);
}
