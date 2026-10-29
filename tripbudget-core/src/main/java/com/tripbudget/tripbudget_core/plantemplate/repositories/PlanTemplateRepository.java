package com.tripbudget.tripbudget_core.plantemplate.repositories;

import com.tripbudget.tripbudget_core.plantemplate.entities.PlanTemplateEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PlanTemplateRepository extends JpaRepository<PlanTemplateEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT template FROM PlanTemplateEntity template WHERE template.id = :templateId")
    Optional<PlanTemplateEntity> findByIdForUpdate(@Param("templateId") Long templateId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT template FROM PlanTemplateEntity template WHERE template.id = :templateId")
    Optional<PlanTemplateEntity> findByIdForCopy(@Param("templateId") Long templateId);

    boolean existsByCoverUploadId(UUID coverUploadId);

    Optional<PlanTemplateEntity> findBySourceTripId(Long tripId);
}
