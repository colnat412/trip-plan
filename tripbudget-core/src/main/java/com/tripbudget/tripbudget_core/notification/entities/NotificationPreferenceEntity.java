package com.tripbudget.tripbudget_core.notification.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(schema = "trip_core", name = "notification_preferences")
@Getter
@NoArgsConstructor
public class NotificationPreferenceEntity {
    @Id private Long userId;
    @Column(nullable = false) private boolean pushEnabled;
    @Column(nullable = false, length = 2) private String locale = "vi";
    @Column(nullable = false) private Instant remindersEnabledAt = Instant.now();
    public static NotificationPreferenceEntity defaults(Long userId) {
        NotificationPreferenceEntity preference = new NotificationPreferenceEntity();
        preference.userId = userId;
        return preference;
    }
    public void update(boolean push, String locale, Instant now) {
        if (push && !pushEnabled) remindersEnabledAt = now;
        pushEnabled = push;
        this.locale = locale;
    }
}
