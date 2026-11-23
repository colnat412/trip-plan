package com.tripbudget.tripbudget_core.user.entities;

import com.tripbudget.tripbudget_core.common.entities.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "trip_core", name = "users")
@DynamicUpdate
@Getter
@NoArgsConstructor
public class UserEntity extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(nullable = false)
    private String status;

    @Column(name = "status_before_deletion", length = 255)
    private String statusBeforeDeletion;

    private String password;

    @Column(name = "avatar_upload_id")
    private UUID avatarUploadId;

    @Column(name = "policies_accepted_at")
    private Instant policiesAcceptedAt;

    @Column(name = "policies_version", length = 32)
    private String policiesVersion;

    @Column(name = "policies_locale", length = 2)
    private String policiesLocale;

    public static UserEntity register(String email, String name, String password) {
        UserEntity user = new UserEntity();
        user.email = email;
        user.updateRegistration(name, password);
        user.status = "INACTIVE";
        return user;
    }

    public static UserEntity fromGoogle(String email, String name, String picture) {
        UserEntity user = register(email, name, null);
        user.updateGoogleProfile(picture);
        return user;
    }

    public void updateRegistration(String name, String password) {
        this.name = name;
        this.password = password;
    }

    public void activate() {
        status = "ACTIVE";
    }

    public void setDisabled(boolean disabled) {
        if (disabled) {
            if ("DELETED".equals(status)) return;
            statusBeforeDeletion = "ACTIVE".equals(status) ? "ACTIVE" : "INACTIVE";
            status = "DELETED";
        } else if ("DELETED".equals(status) || "BLOCKED".equals(status)) {
            // Never infer verification for a legacy disabled account with no saved state.
            status = "ACTIVE".equals(statusBeforeDeletion) ? "ACTIVE" : "INACTIVE";
            statusBeforeDeletion = null;
        }
    }

    public void changeEmail(String email) {
        this.email = email;
        if ("DELETED".equals(status)) statusBeforeDeletion = "INACTIVE";
        else status = "INACTIVE";
    }

    public void updateGoogleProfile(String picture) {
        if (picture != null && !picture.isEmpty() && (avatarUrl == null || avatarUrl.isEmpty())) {
            avatarUrl = picture;
        }
        activate();
    }

    public void updateName(String name) {
        this.name = name;
    }

    public boolean hasPassword() {
        return password != null && !password.isBlank();
    }

    public void updatePassword(String passwordHash) {
        this.password = passwordHash;
    }

    public void updateAvatar(UUID uploadId, String url) {
        avatarUploadId = uploadId;
        avatarUrl = url;
    }
}
