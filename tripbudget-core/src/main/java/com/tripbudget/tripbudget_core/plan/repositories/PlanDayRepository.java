package com.tripbudget.tripbudget_core.plan.repositories;

import com.tripbudget.tripbudget_core.plan.entities.PlanDayEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlanDayRepository extends JpaRepository<PlanDayEntity, Long> {

    List<PlanDayEntity> findAllByTripIdAndIsDelFalseOrderByDayNumberAsc(Long tripId);

    Optional<PlanDayEntity> findByIdAndTripIdAndIsDelFalse(Long id, Long tripId);

    Optional<PlanDayEntity> findByTripIdAndDayNumberAndIsDelFalse(Long tripId, Integer dayNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT day FROM PlanDayEntity day WHERE day.id = :dayId AND day.tripId = :tripId AND day.isDel = false")
    Optional<PlanDayEntity> findForUpdate(@Param("dayId") Long dayId, @Param("tripId") Long tripId);

    boolean existsByTripIdAndIsDelFalse(Long tripId);
}

