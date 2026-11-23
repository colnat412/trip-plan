package com.tripbudget.tripbudget_core.admin.dtos;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

public final class AdminDtos {
    private AdminDtos() {}

    public record User(
            String id,
            String name,
            String email,
            String status,
            String avatarUrl,
            boolean hasPassword,
            boolean admin,
            Instant policiesAcceptedAt,
            String policiesVersion,
            String policiesLocale,
            Instant createdAt,
            Instant updatedAt,
            String statusBeforeDeletion) {}

    public record PageResult<T>(
            List<T> items, int page, int size, long totalElements, int totalPages) {}

    public record Session(
            String id, String userId, Instant createdAt, Instant expiresAt, boolean current) {}

    public record Sessions(User user, List<Session> items) {}

    public record Overview(long users, long activeUsers, long pendingUsers, long disabledUsers) {}

    public record Audit(
            String id,
            String actor,
            String action,
            String target,
            String reason,
            String outcome,
            Instant createdAt) {}

    public record Provider(String name, boolean configured) {}

    public record Settings(
            String consoleName,
            String supportEmail,
            int sessionWarningMinutes,
            long version,
            List<Provider> providers) {}

    public record ChangeStatus(
            @NotNull Boolean disabled, @NotBlank @Size(min = 3, max = 500) String reason) {}

    public record UpdateUser(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotNull Boolean removeAvatar,
            @NotBlank @Size(min = 3, max = 500) String reason) {}

    public record Reason(@NotBlank @Size(min = 3, max = 500) String reason) {}

    public record UpdateSettings(
            @NotBlank @Size(max = 80) String consoleName,
            @Email @Size(max = 254) String supportEmail,
            @Min(1) @Max(1440) int sessionWarningMinutes,
            @Min(0) long version,
            @NotBlank @Size(min = 3, max = 500) String reason) {}

    public record Revocation(int revoked) {}
}
