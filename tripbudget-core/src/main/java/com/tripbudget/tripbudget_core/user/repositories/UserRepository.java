package com.tripbudget.tripbudget_core.user.repositories;

import com.tripbudget.tripbudget_core.user.entities.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM trip_core.users
                WHERE id = :id
                  AND policies_accepted_at IS NOT NULL
                  AND policies_version IS NOT NULL
            )
            """, nativeQuery = true)
    boolean hasAcceptedPolicies(@Param("id") Long id);

    Optional<UserEntity> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT user FROM UserEntity user WHERE user.id = :id")
    Optional<UserEntity> findForUpdateById(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE UserEntity user
            SET user.policiesAcceptedAt = :acceptedAt,
                user.policiesVersion = :version,
                user.policiesLocale = :locale
            WHERE user.id = :id AND user.policiesAcceptedAt IS NULL
            """)
    int acceptPoliciesIfAbsent(@Param("id") Long id, @Param("acceptedAt") Instant acceptedAt,
                               @Param("version") String version, @Param("locale") String locale);

    List<UserEntity> findAllByIdIn(Collection<Long> ids);

    @Query("""
            SELECT user.id
            FROM UserEntity user
            WHERE LOWER(user.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(user.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
            """)
    List<Long> findIdsByKeyword(@Param("keyword") String keyword);
}


