package com.tripbudget.tripbudget_core.trip.repositories;

import com.tripbudget.tripbudget_core.trip.entities.TripEntity;
import com.tripbudget.tripbudget_core.trip.enums.TripMemberStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface TripRepository extends JpaRepository<TripEntity, Long>, JpaSpecificationExecutor<TripEntity> {
    @Query(
            value = """
            SELECT trip
            FROM TripEntity trip
            JOIN TripMemberEntity tripMember
                ON tripMember.trip = trip
            WHERE tripMember.userId = :currentUserId
              AND tripMember.status = :memberStatus
              AND trip.status != TripStatus.DELETED
            ORDER BY trip.id DESC
            """,
            countQuery = """
            SELECT COUNT(trip)
            FROM TripEntity trip
            JOIN TripMemberEntity tripMember
                ON tripMember.trip = trip
            WHERE tripMember.userId = :currentUserId
              AND tripMember.status = :memberStatus
              AND trip.status != TripStatus.DELETED
            """
    )
    Page<TripEntity> findAllActiveTripsByUserId(
            @Param("currentUserId") Long currentUserId,
            @Param("memberStatus") TripMemberStatus memberStatus,
            Pageable pageable
    );

    @Query("""
        SELECT trip
        FROM TripEntity trip
        WHERE trip.id = :tripId
          AND trip.status != TripStatus.DELETED
    """
    )
    TripEntity findActiveTrip(@Param("tripId") Long tripId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT trip FROM TripEntity trip WHERE trip.id = :tripId")
    Optional<TripEntity> findByIdForUpdate(@Param("tripId") Long tripId);

    boolean existsByCoverUploadId(UUID coverUploadId);

    Optional<TripEntity> findByShareTokenAndIsDelFalse(String shareToken);
}