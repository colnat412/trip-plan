package com.tripbudget.tripbudget_core.admin.entities;

import com.tripbudget.tripbudget_core.common.entities.BaseEntity;

import jakarta.persistence.*;

import lombok.*;

@Getter
@Entity
@Table(schema = "trip_core", name = "admin_settings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminSettingsEntity extends BaseEntity {
    @Column(nullable = false, unique = true, length = 30)
    private String scope;

    @Column(name = "console_name", nullable = false, length = 80)
    private String consoleName;

    @Column(name = "support_email", nullable = false, length = 254)
    private String supportEmail;

    @Column(name = "session_warning_minutes", nullable = false)
    private int sessionWarningMinutes;

    @Version private long version;

    public void update(String name, String email, int warningMinutes) {
        consoleName = name.trim();
        supportEmail = email.trim();
        sessionWarningMinutes = warningMinutes;
    }
}
