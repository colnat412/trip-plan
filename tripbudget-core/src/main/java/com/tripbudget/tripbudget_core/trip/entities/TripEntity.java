package com.tripbudget.tripbudget_core.trip.entities;

import com.tripbudget.tripbudget_core.common.entities.BaseEntity;
import com.tripbudget.tripbudget_core.trip.enums.TripStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;

@Getter
@Entity
@Table(
        schema = "trip_core",
        name = "trips"
)
@NoArgsConstructor(access = AccessLevel.PROTECTED) // set to service only use .create(...) cannot use new TripEntity();
public class TripEntity extends BaseEntity {

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 255)
    private String destination;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TripStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private com.tripbudget.tripbudget_core.trip.enums.TripVisibility visibility = com.tripbudget.tripbudget_core.trip.enums.TripVisibility.PRIVATE;

    @Enumerated(EnumType.STRING)
    @Column(name = "public_role", nullable = false, length = 30)
    private com.tripbudget.tripbudget_core.trip.enums.TripMemberRole publicRole = com.tripbudget.tripbudget_core.trip.enums.TripMemberRole.VIEWER;

    @Column(name = "share_token", unique = true, length = 64)
    private String shareToken;

    @Column(name = "cover_upload_id")
    private java.util.UUID coverUploadId;

    @Column(name = "cover_url", columnDefinition = "TEXT")
    private String coverUrl;

    public void updateCover(java.util.UUID uploadId, String url) {
        this.coverUploadId = uploadId;
        this.coverUrl = url;
    }

    @Version
    @Column(nullable = false)
    private Long version;

    // default set value to create trip, prevent set incorrectly value
    public static TripEntity create(
            Long ownerId,
            String name,
            String destination,
            String description,
            LocalDate startDate,
            LocalDate endDate,
            String baseCurrency
    ) {
        TripEntity trip = new TripEntity();

        trip.ownerId = ownerId;
        trip.name = name.trim();
        trip.destination = trimToNull(destination);
        trip.description = trimToNull(description);
        trip.startDate = startDate;
        trip.endDate = endDate;

        trip.baseCurrency = baseCurrency == null
                ? "VND"
                : baseCurrency.toUpperCase(Locale.ROOT);

        // always DRAFT when create trip
        trip.status = TripStatus.DRAFT;
        trip.visibility = com.tripbudget.tripbudget_core.trip.enums.TripVisibility.PRIVATE;
        trip.publicRole = com.tripbudget.tripbudget_core.trip.enums.TripMemberRole.VIEWER;
        trip.shareToken = java.util.UUID.randomUUID().toString().replace("-", "");

        return trip;
    }

    public void updateShareSettings(
            com.tripbudget.tripbudget_core.trip.enums.TripVisibility visibility,
            com.tripbudget.tripbudget_core.trip.enums.TripMemberRole publicRole
    ) {
        if (visibility != null) {
            this.visibility = visibility;
        }
        if (publicRole != null) {
            this.publicRole = publicRole;
        }
        if (this.shareToken == null || this.shareToken.isBlank()) {
            this.shareToken = java.util.UUID.randomUUID().toString().replace("-", "");
        }
    }

    public void regenerateShareToken() {
        this.shareToken = java.util.UUID.randomUUID().toString().replace("-", "");
    }

    public void update(
            String name,
            String description,
            String destination,
            LocalDate startDate,
            LocalDate endDate,
            String baseCurrency,
            TripStatus status
    ) {
        if (name != null) {
            this.name = name.trim();
        }

        if (description != null) {
            this.description = description;
        }

        if (destination != null) {
            this.destination = destination;
        }

        if (startDate != null) {
            this.startDate = startDate;
        }

        if (endDate != null) {
            this.endDate = endDate;
        }

        if (baseCurrency != null) {
            this.baseCurrency = baseCurrency
                    .trim()
                    .toUpperCase(Locale.ROOT);
        }

        if (status != null && status != TripStatus.DELETED) {
            this.status = status;
        }
    }

    public void update(
            String name,
            String description,
            String destination,
            LocalDate startDate,
            LocalDate endDate,
            String baseCurrency
    ) {
        this.update(name, description, destination, startDate, endDate, baseCurrency, null);
    }

    public void changeStatus(TripStatus newStatus) {
        if (newStatus != null && newStatus != TripStatus.DELETED) {
            this.status = newStatus;
        }
    }

    public void deleteTrip() {
        this.status = TripStatus.DELETED;
        this.markDeleted();
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}