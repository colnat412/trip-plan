package com.tripbudget.tripbudget_core.expense.repositories;

import com.tripbudget.tripbudget_core.expense.entities.ExpenseEntity;
import com.tripbudget.tripbudget_core.expense.repositories.projections.CategorySpendingTotal;
import com.tripbudget.tripbudget_core.expense.repositories.projections.ActivitySpendingTotal;
import com.tripbudget.tripbudget_core.expense.enums.ExpenseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseRepository extends JpaRepository<ExpenseEntity, Long>, JpaSpecificationExecutor<ExpenseEntity> {

    Page<ExpenseEntity> findByTripIdAndStatusNotAndIsDelFalseOrderByExpenseDateDesc(
            Long tripId,
            ExpenseStatus status,
            Pageable pageable
    );

    List<ExpenseEntity> findByTripIdAndStatusNotAndIsDelFalseOrderByExpenseDateDesc(
            Long tripId,
            ExpenseStatus status
    );

    Optional<ExpenseEntity> findByIdAndTripIdAndIsDelFalse(Long id, Long tripId);

    Optional<ExpenseEntity> findByIdAndIsDelFalse(Long id);

    @Query("""
            SELECT COALESCE(SUM(expense.amount), 0)
            FROM ExpenseEntity expense
            WHERE expense.tripId = :tripId
              AND expense.status = :status
              AND expense.isDel = false
            """)
    BigDecimal sumAmountByTripIdAndStatus(
            @Param("tripId") Long tripId,
            @Param("status") ExpenseStatus status
    );

    @Query("""
            SELECT expense.category AS category, COALESCE(SUM(expense.amount), 0) AS totalSpent
            FROM ExpenseEntity expense
            WHERE expense.tripId = :tripId
              AND expense.status = :status
              AND expense.isDel = false
            GROUP BY expense.category
            """)
    List<CategorySpendingTotal> sumAmountByCategory(
            @Param("tripId") Long tripId,
            @Param("status") ExpenseStatus status
    );

    @Query("""
            SELECT expense.activityId AS activityId, COALESCE(SUM(expense.amount), 0) AS totalSpent
            FROM ExpenseEntity expense
            WHERE expense.tripId = :tripId
              AND expense.activityId IS NOT NULL
              AND expense.status <> com.tripbudget.tripbudget_core.expense.enums.ExpenseStatus.DELETED
              AND expense.isDel = false
            GROUP BY expense.activityId
            """)
    List<ActivitySpendingTotal> sumAmountByTripIdAndActivityGrouped(@Param("tripId") Long tripId);

    @Query("""
            SELECT COALESCE(SUM(expense.amount), 0)
            FROM ExpenseEntity expense
            WHERE expense.tripId = :tripId
              AND expense.activityId = :activityId
              AND expense.status <> com.tripbudget.tripbudget_core.expense.enums.ExpenseStatus.DELETED
              AND expense.isDel = false
            """)
    BigDecimal sumAmountByActivityId(@Param("tripId") Long tripId, @Param("activityId") Long activityId);
}
