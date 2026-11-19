package com.tripbudget.tripbudget_core.notification.repositories;

import com.tripbudget.tripbudget_core.notification.entities.NotificationPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreferenceEntity, Long> {
    @org.springframework.data.jpa.repository.Query(value = "select * from trip_core.notification_preferences for update skip locked", nativeQuery = true)
    List<NotificationPreferenceEntity> lockReminderPreferences();
}
