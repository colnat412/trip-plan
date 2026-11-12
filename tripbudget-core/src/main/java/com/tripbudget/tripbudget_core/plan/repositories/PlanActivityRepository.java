package com.tripbudget.tripbudget_core.plan.repositories;

import com.tripbudget.tripbudget_core.plan.entities.PlanActivityEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlanActivityRepository extends JpaRepository<PlanActivityEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM PlanActivityEntity a WHERE a.id = :id AND a.tripId = :tripId AND a.isDel = false AND a.day.isDel = false")
    Optional<PlanActivityEntity> findActiveForUpdate(@Param("id") Long id, @Param("tripId") Long tripId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM PlanActivityEntity a WHERE a.day.id = :dayId AND a.isDel = false ORDER BY a.id")
    List<PlanActivityEntity> findAllActiveForUpdateByDayId(@Param("dayId") Long dayId);

    List<PlanActivityEntity> findAllByTripIdAndIsDelFalseOrderByOrderIndexAsc(Long tripId);

    List<PlanActivityEntity> findAllByDay_IdAndIsDelFalseOrderByOrderIndexAscStartTimeAsc(Long dayId);

    @Query("""
            SELECT activity
            FROM PlanActivityEntity activity
            WHERE activity.day.id = :dayId
              AND activity.isDel = false
            ORDER BY activity.orderIndex ASC, activity.startTime ASC NULLS LAST, activity.createdAt ASC
            """)
    List<PlanActivityEntity> findAllByDayIdSorted(@Param("dayId") Long dayId);

    @Query(
            value = """
                    SELECT activity
                    FROM PlanActivityEntity activity
                    WHERE activity.day.id = :dayId
                      AND activity.isDel = false
                    ORDER BY activity.orderIndex ASC, activity.startTime ASC NULLS LAST,
                             activity.createdAt ASC, activity.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(activity)
                    FROM PlanActivityEntity activity
                    WHERE activity.day.id = :dayId
                      AND activity.isDel = false
                    """
    )
    Page<PlanActivityEntity> findPageByDayIdSorted(@Param("dayId") Long dayId, Pageable pageable);

    Optional<PlanActivityEntity> findByIdAndTripIdAndIsDelFalse(Long id, Long tripId);

    Optional<PlanActivityEntity> findByExpenseIdAndIsDelFalse(Long expenseId);

    List<PlanActivityEntity> findAllByExpenseIdAndIsDelFalse(Long expenseId);

    List<PlanActivityEntity> findAllByExpenseIdAndTripIdAndIsDelFalse(Long expenseId, Long tripId);
}

