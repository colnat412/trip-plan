package com.tripbudget.tripbudget_core.plantemplate.repositories;

import com.tripbudget.tripbudget_core.plantemplate.entities.PlanTemplateCopyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlanTemplateCopyRepository extends JpaRepository<PlanTemplateCopyEntity, Long> {
    Optional<PlanTemplateCopyEntity> findByUserIdAndIdempotencyKey(Long userId, UUID idempotencyKey);
}
