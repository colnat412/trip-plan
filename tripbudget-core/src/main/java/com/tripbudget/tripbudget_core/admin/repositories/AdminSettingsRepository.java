package com.tripbudget.tripbudget_core.admin.repositories;

import com.tripbudget.tripbudget_core.admin.entities.AdminSettingsEntity;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;

import java.util.Optional;

public interface AdminSettingsRepository extends JpaRepository<AdminSettingsEntity, Long> {
    Optional<AdminSettingsEntity> findByScopeAndIsDelFalse(String scope);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select settings from AdminSettingsEntity settings where settings.scope = :scope and"
                + " settings.isDel = false")
    Optional<AdminSettingsEntity> findForUpdateByScope(String scope);
}
