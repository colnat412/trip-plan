package com.tripbudget.tripbudget_core.user.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(nullable = false)
    private String status;

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

    public void updateGoogleProfile(String picture) {
        if (picture != null && !picture.isEmpty() && (avatarUrl == null || avatarUrl.isEmpty())) {
            avatarUrl = picture;
        }
        activate();
    }

    public void updateName(String name) {
        this.name = name;
    }

    public void updateAvatar(UUID uploadId, String url) {
        avatarUploadId = uploadId;
        avatarUrl = url;
    }
}

