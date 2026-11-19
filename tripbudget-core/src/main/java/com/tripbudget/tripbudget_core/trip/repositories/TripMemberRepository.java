package com.tripbudget.tripbudget_core.trip.repositories;

import com.tripbudget.tripbudget_core.trip.entities.TripMemberEntity;
import com.tripbudget.tripbudget_core.trip.enums.TripMemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TripMemberRepository extends JpaRepository<TripMemberEntity, Long> {
    @EntityGraph(attributePaths = "trip")
    List<TripMemberEntity> findByUserIdAndTrip_IdIn(Long userId, Collection<Long> tripIds);

    List<TripMemberEntity> findAllByUserIdAndStatusAndIsDelFalse(Long userId, TripMemberStatus status);

    Optional<TripMemberEntity> findByTrip_IdAndUserId(
            Long tripId,
            Long userId
    );

    boolean existsByTrip_IdAndUserIdAndStatus(
            Long tripId,
            Long userId,
            TripMemberStatus status
    );

    List<TripMemberEntity> findAllByTrip_IdAndStatus(
            Long tripId,
            TripMemberStatus status
    );

    List<TripMemberEntity> findByTrip_IdAndIsDelFalseOrderByCreatedAtAsc(
            Long tripId
    );

    Optional<TripMemberEntity> findByIdAndTrip_IdAndIsDelFalse(
            Long id,
            Long tripId
    );

    Optional<TripMemberEntity> findByTrip_IdAndUserIdAndIsDelFalse(
            Long tripId,
            Long userId
    );
}
