package com.tripbudget.tripbudget_core.expense.repositories;

import com.tripbudget.tripbudget_core.expense.entities.BudgetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BudgetRepository extends JpaRepository<BudgetEntity, Long> {

    Optional<BudgetEntity> findByTripIdAndIsDelFalse(Long tripId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT budget FROM BudgetEntity budget WHERE budget.tripId = :tripId AND budget.isDel = false")
    Optional<BudgetEntity> findForUpdate(Long tripId);
}

