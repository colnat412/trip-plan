package com.tripbudget.tripbudget_core.settlement.repositories;

import com.tripbudget.tripbudget_core.settlement.entities.SettlementEntity;
import com.tripbudget.tripbudget_core.settlement.enums.SettlementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface SettlementRepository extends JpaRepository<SettlementEntity, Long> {

    List<SettlementEntity> findByTripIdAndIsDelFalseOrderBySettledAtDescCreatedAtDesc(Long tripId);

    boolean existsByTripIdAndPayerIdAndPayeeIdAndCurrencyAndStatusAndIsDelFalse(
            Long tripId, Long payerId, Long payeeId, String currency, SettlementStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SettlementEntity> findByIdAndTripIdAndIsDelFalse(Long id, Long tripId);

    @Query("""
            SELECT COALESCE(SUM(settlement.amount), 0)
            FROM SettlementEntity settlement
            WHERE settlement.tripId = :tripId
              AND settlement.isDel = false
              AND settlement.status IN (com.tripbudget.tripbudget_core.settlement.enums.SettlementStatus.CONFIRMED,
                  com.tripbudget.tripbudget_core.settlement.enums.SettlementStatus.DELETE_PENDING)
            """)
    BigDecimal sumAmountByTripId(@Param("tripId") Long tripId);
}

