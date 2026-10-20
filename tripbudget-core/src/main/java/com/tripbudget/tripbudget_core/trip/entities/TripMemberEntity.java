package com.tripbudget.tripbudget_core.trip.entities;

import com.tripbudget.tripbudget_core.common.entities.BaseEntity;
import com.tripbudget.tripbudget_core.trip.enums.TripMemberRole;
import com.tripbudget.tripbudget_core.trip.enums.TripMemberStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(
        schema = "trip_core",
        name = "trip_members",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_trip_members_trip_user",
                        columnNames = {"trip_id", "user_id"}
                )
        }
)
@NoArgsConstructor
public class TripMemberEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private TripEntity trip;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TripMemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TripMemberStatus status;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    public static TripMemberEntity createOwner(TripEntity trip, Long userId) {
        TripMemberEntity member = new TripMemberEntity();

        member.trip = trip;
        member.userId = userId;
        member.role = TripMemberRole.OWNER;
        member.status = TripMemberStatus.ACTIVE;

        return member;
    }

    public static TripMemberEntity invite(
            TripEntity trip,
            Long userId,
            TripMemberRole role
    ) {
        if (role == TripMemberRole.OWNER) {
            throw new IllegalArgumentException(
                "Inviting member cannot have OWNER role"
            );
        }

        TripMemberEntity member = new TripMemberEntity();
        member.trip = trip;
        member.userId = userId;
        member.role = role;
        member.status = TripMemberStatus.INVITED;

        return member;
    }

    public static TripMemberEntity addDirectMember(TripEntity trip, Long userId, TripMemberRole role) {
        if (role == TripMemberRole.OWNER) {
            throw new IllegalArgumentException("Directly added member cannot have OWNER role");
        }

        TripMemberEntity member = new TripMemberEntity();
        member.trip = trip;
        member.userId = userId;
        member.role = role;
        member.status = TripMemberStatus.ACTIVE;
        member.joinedAt = Instant.now();

        return member;
    }

    public void updateRole(TripMemberRole newRole) {
        if (newRole == null) {
            throw new IllegalArgumentException("Role cannot be null");
        }
        if (this.role == TripMemberRole.OWNER || newRole == TripMemberRole.OWNER) {
            throw new IllegalArgumentException("Cannot change member role to OWNER directly");
        }
        this.role = newRole;
    }

    public void updateStatus(TripMemberStatus newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
        this.status = newStatus;
        if (newStatus == TripMemberStatus.ACTIVE && this.joinedAt == null) {
            this.joinedAt = Instant.now();
        }
    }

    public void remove() {
        this.status = TripMemberStatus.REMOVED;
        this.markDeleted();
    }

    public void leave() {
        this.status = TripMemberStatus.LEFT;
        this.markDeleted();
    }

    public void reactivate(TripMemberRole newRole, TripMemberStatus newStatus) {
        this.role = newRole;
        this.status = newStatus;
        this.restore();
        if (newStatus == TripMemberStatus.ACTIVE) {
            this.joinedAt = Instant.now();
        }
    }

    @PrePersist
    private void prePersistMember() {
        if (joinedAt == null) {
            joinedAt = Instant.now();
        }
    }
}