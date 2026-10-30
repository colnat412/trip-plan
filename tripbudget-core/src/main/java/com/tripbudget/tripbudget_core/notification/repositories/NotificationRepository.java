package com.tripbudget.tripbudget_core.notification.repositories;

import com.tripbudget.tripbudget_core.notification.entities.NotificationEntity;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {
    // One predicate shared by list/count/target so revoked memberships cannot leak details.
    String VISIBLE = """
        n.recipientId = :userId AND n.isDel = false
        AND EXISTS (SELECT t.id FROM TripEntity t WHERE t.id = n.tripId
            AND t.isDel = false AND t.status <> com.tripbudget.tripbudget_core.trip.enums.TripStatus.DELETED)
        AND EXISTS (SELECT m.id FROM TripMemberEntity m WHERE m.trip.id = n.tripId
            AND m.userId = :userId AND m.isDel = false
            AND (m.status = com.tripbudget.tripbudget_core.trip.enums.TripMemberStatus.ACTIVE
                OR (n.type = com.tripbudget.tripbudget_core.notification.enums.NotificationType.TRIP_INVITATION_PENDING
                    AND m.status = com.tripbudget.tripbudget_core.trip.enums.TripMemberStatus.INVITED)))
        """;

    @Query("SELECT n FROM NotificationEntity n WHERE " + VISIBLE
        + " AND (:unreadOnly = false OR n.readAt IS NULL)")
    Page<NotificationEntity> findVisible(@Param("userId") Long userId,
            @Param("unreadOnly") boolean unreadOnly, Pageable pageable);

    @Query("SELECT COUNT(n) FROM NotificationEntity n WHERE " + VISIBLE + " AND n.readAt IS NULL")
    long countUnread(@Param("userId") Long userId);

    @Query("SELECT n FROM NotificationEntity n WHERE n.id = :id AND " + VISIBLE)
    Optional<NotificationEntity> findVisibleById(@Param("userId") Long userId, @Param("id") Long id);

    @Modifying
    @Query("UPDATE NotificationEntity n SET n.readAt = :now, n.updatedAt = :now WHERE "
        + VISIBLE + " AND n.readAt IS NULL AND (:id IS NULL OR n.id = :id)")
    int markRead(@Param("userId") Long userId, @Param("id") Long id, @Param("now") Instant now);
}
