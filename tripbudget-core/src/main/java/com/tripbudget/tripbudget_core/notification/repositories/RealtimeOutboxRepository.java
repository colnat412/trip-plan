package com.tripbudget.tripbudget_core.notification.repositories;

import com.tripbudget.tripbudget_core.notification.entities.RealtimeOutboxEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

public interface RealtimeOutboxRepository extends JpaRepository<RealtimeOutboxEntity, Long> {
    String DUE = "o.status IN ('PENDING', 'PROCESSING') AND o.nextAttemptAt <= :now "
            + "AND (o.leaseUntil IS NULL OR o.leaseUntil < :now)";
    @Query("SELECT o FROM RealtimeOutboxEntity o WHERE " + DUE + " ORDER BY o.id")
    List<RealtimeOutboxEntity> findDue(@Param("now") Instant now, Pageable pageable);

    @Modifying
    @Query("UPDATE RealtimeOutboxEntity o SET o.status = 'PROCESSING', o.claimToken = :token, "
        + "o.leaseUntil = :until, o.attempts = o.attempts + 1, o.updatedAt = :now "
        + "WHERE o.id = :id AND " + DUE)
    int claim(@Param("id") Long id, @Param("token") UUID token,
            @Param("now") Instant now, @Param("until") Instant until);

    @Modifying
    @Query("UPDATE RealtimeOutboxEntity o SET o.status = :status, o.nextAttemptAt = :next, "
        + "o.leaseUntil = NULL, o.claimToken = NULL, o.dispatchedAt = :dispatched, o.updatedAt = :now "
        + "WHERE o.id = :id AND o.claimToken = :token")
    int finish(@Param("id") Long id, @Param("token") UUID token, @Param("status") String status,
            @Param("next") Instant next, @Param("dispatched") Instant dispatched, @Param("now") Instant now);

    @Query("SELECT o.id FROM RealtimeOutboxEntity o WHERE o.status = 'DISPATCHED' "
        + "AND o.dispatchedAt < :before ORDER BY o.id")
    List<Long> findDispatchedBefore(@Param("before") Instant before, Pageable page);
}
