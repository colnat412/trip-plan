package com.tripbudget.tripbudget_core.plan.repositories;

import com.tripbudget.tripbudget_core.plan.entities.PlanActivityEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlanActivityRepository extends JpaRepository<PlanActivityEntity, Long> {

    List<PlanActivityEntity> findAllByTripIdAndIsDelFalseOrderByOrderIndexAsc(Long tripId);

    List<PlanActivityEntity> findAllByDay_IdAndIsDelFalseOrderByOrderIndexAscStartTimeAsc(Long dayId);

    @Query("""
            SELECT activity
            FROM PlanActivityEntity activity
            WHERE activity.day.id = :dayId
              AND activity.isDel = false
            ORDER BY activity.startTime ASC NULLS LAST, activity.orderIndex ASC, activity.createdAt ASC
            """)
    List<PlanActivityEntity> findAllByDayIdSorted(@Param("dayId") Long dayId);

    @Query(
            value = """
                    SELECT activity
                    FROM PlanActivityEntity activity
                    WHERE activity.day.id = :dayId
                      AND activity.isDel = false
                    ORDER BY activity.startTime ASC NULLS LAST, activity.orderIndex ASC,
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
}

