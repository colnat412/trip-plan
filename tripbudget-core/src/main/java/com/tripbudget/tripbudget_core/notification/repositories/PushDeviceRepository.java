package com.tripbudget.tripbudget_core.notification.repositories;

import com.tripbudget.tripbudget_core.notification.entities.PushDeviceEntity;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface PushDeviceRepository extends JpaRepository<PushDeviceEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PushDeviceEntity> findByInstallationId(UUID installationId);
    Optional<PushDeviceEntity> findByAddressValue(String addressValue);
    List<PushDeviceEntity> findByUserIdAndEnabledTrue(Long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PushDeviceEntity> findByUserIdAndSessionIdAndEnabledTrue(Long userId, String sessionId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from PushDeviceEntity d where d.id = :id")
    Optional<PushDeviceEntity> lockById(Long id);
}
