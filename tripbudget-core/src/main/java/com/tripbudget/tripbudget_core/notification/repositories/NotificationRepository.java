package com.tripbudget.tripbudget_core.notification.repositories;

import com.tripbudget.tripbudget_core.notification.entities.NotificationEntity;
import com.tripbudget.tripbudget_core.notification.enums.NotificationType;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.Instant;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {
    // The inbox belongs to the recipient, even after trip access is revoked.
    // Live trip authorization is checked separately before returning navigation links.
    String ACCESS = "n.isDel = false and n.recipientId = :userId";
    @Query("select n from NotificationEntity n where " + ACCESS + " and (:hasCursor = false or n.createdAt < :before or (n.createdAt = :before and n.id < :beforeId)) and (:unread = false or n.readAt is null) order by n.createdAt desc, n.id desc")
    List<NotificationEntity> inbox(Long userId, Instant before, Long beforeId, boolean hasCursor, boolean unread, Pageable pageable);
    @Query("select count(n) from NotificationEntity n where " + ACCESS + " and n.readAt is null")
    long unread(Long userId);
    @Query("select n from NotificationEntity n where " + ACCESS + " and n.id = :id")
    Optional<NotificationEntity> accessible(Long userId, Long id);
    @Modifying
    @Query("update NotificationEntity n set n.readAt = :now where " + ACCESS + " and n.readAt is null and n.createdAt <= :through")
    int readAll(Long userId, Instant through, Instant now);
    @Query("select n.tripId from NotificationEntity n where n.id = :id and n.recipientId = :userId and n.isDel = false")
    Optional<Long> ownedTripId(Long userId, Long id);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from NotificationEntity n where n.id = :id")
    Optional<NotificationEntity> lockById(Long id);
    List<NotificationEntity> findByTargetIdAndType(Long targetId, NotificationType type);
    @Query("select n.targetId from NotificationEntity n where n.tripId = :tripId and n.type = NotificationType.MEMBER_INVITED")
    Set<Long> pendingInvitationMemberIds(Long tripId);
    boolean existsByTargetIdAndType(Long targetId, NotificationType type);
    boolean existsByDedupeKey(String dedupeKey);
}
