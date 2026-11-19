package com.tripbudget.tripbudget_core.notification.repositories;

import com.tripbudget.tripbudget_core.notification.entities.NotificationDeliveryEntity;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.time.Instant;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDeliveryEntity, Long> {
    @Query(value = """
        select * from trip_core.notification_deliveries
        where is_del = false and ((status = 'PENDING' and next_attempt_at <= :now)
           or (status = 'SENDING' and locked_until <= :now))
        order by next_attempt_at, id limit 1 for update skip locked
        """, nativeQuery = true)
    List<NotificationDeliveryEntity> ready(Instant now);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from NotificationDeliveryEntity d where d.id = :id")
    Optional<NotificationDeliveryEntity> lockById(Long id);
}
