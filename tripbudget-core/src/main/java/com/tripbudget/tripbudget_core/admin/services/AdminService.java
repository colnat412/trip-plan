package com.tripbudget.tripbudget_core.admin.services;

import com.tripbudget.tripbudget_core.admin.access.AdminAccess;
import com.tripbudget.tripbudget_core.admin.dtos.AdminDtos;
import com.tripbudget.tripbudget_core.admin.entities.*;
import com.tripbudget.tripbudget_core.admin.repositories.*;
import com.tripbudget.tripbudget_core.auth.session.AuthSessionService;
import com.tripbudget.tripbudget_core.common.services.HashidsService;
import com.tripbudget.tripbudget_core.notification.services.NotificationService;
import com.tripbudget.tripbudget_core.user.entities.UserEntity;
import com.tripbudget.tripbudget_core.user.repositories.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {
    private final AdminAccess access;
    private final UserRepository users;
    private final AuthSessionService authSessions;
    private final NotificationService notifications;
    private final AdminAuditRepository auditLogs;
    private final AdminSettingsRepository settings;
    private final HashidsService hashids;
    private final Environment environment;
    private final com.tripbudget.tripbudget_core.user.services.UserAvatarService avatars;

    public AdminDtos.User me(Long actorId) {
        return userDto(access.requireAdmin(actorId));
    }

    public AdminDtos.Overview overview(Long actorId) {
        access.requireAdmin(actorId);
        return new AdminDtos.Overview(
                users.count(),
                users.countByStatus("ACTIVE"),
                users.countByStatus("INACTIVE"),
                users.countByStatus("DELETED") + users.countByStatus("BLOCKED"));
    }

    public AdminDtos.PageResult<AdminDtos.User> users(
            Long actorId, String query, String status, int page, int size) {
        access.requireAdmin(actorId);
        String term = query == null ? "" : query.trim();
        if (term.length() > 100
                || !Set.of("", "ACTIVE", "INACTIVE", "DELETED", "BLOCKED").contains(status))
            throw bad("INVALID_FILTER");
        var result = users.searchForAdmin(term, status, pageRequest(page, size));
        return new AdminDtos.PageResult<>(
                result.getContent().stream().map(this::userDto).toList(),
                page,
                size,
                result.getTotalElements(),
                result.getTotalPages());
    }

    public AdminDtos.User user(Long actorId, String id) {
        access.requireAdmin(actorId);
        return userDto(target(id));
    }

    public AdminDtos.Sessions sessions(Long actorId, String currentSession, String id, int page, int size) {
        access.requireAdmin(actorId);
        var target = target(id);
        var result = authSessions.history(target.getId(), actorId.equals(target.getId()) ? currentSession : null, page, size);
        return new AdminDtos.Sessions(userDto(target), result.items(), result.page(), result.size(),
                result.totalElements(), result.totalPages(), result.activeCount(), result.currentSessionActive());
    }

    @Transactional
    public AdminDtos.User changeStatus(Long actorId, String id, AdminDtos.ChangeStatus request) {
        var actor = access.requireAdmin(actorId);
        var target =
                users.findForUpdateById(hashids.decode(id))
                        .orElseThrow(() -> missing("USER_NOT_FOUND"));
        if (actorId.equals(target.getId()) || access.isAdminEmail(target.getEmail()))
            throw conflict("ADMIN_ACCOUNT_PROTECTED");
        String reason = reason(request.reason());
        if (request.disabled() == null) throw bad("INVALID_STATUS");
        boolean disabled =
                "DELETED".equals(target.getStatus()) || "BLOCKED".equals(target.getStatus());
        if (request.disabled() != disabled) {
            target.setDisabled(request.disabled());
            users.save(target);
            auditLogs.save(
                    AdminAuditEntity.create(
                            actorId,
                            actor.getEmail(),
                            "USER_STATUS_CHANGED",
                            target.getEmail(),
                            reason,
                            "SUCCESS"));
            if (request.disabled()) revokeAfterCommit(target);
        }
        return userDto(target);
    }

    @Transactional
    public AdminDtos.User updateUser(Long actorId, String id, AdminDtos.UpdateUser request) {
        var actor = access.requireAdmin(actorId);
        var target =
                users.findForUpdateById(hashids.decode(id))
                        .orElseThrow(() -> missing("USER_NOT_FOUND"));
        String email =
                request.email() == null ? "" : request.email().trim().toLowerCase(Locale.ROOT);
        String name = request.name() == null ? "" : request.name().trim();
        String reason = reason(request.reason());
        if (email.isBlank() || email.length() > 254 || name.isBlank() || name.length() > 100)
            throw bad("INVALID_PROFILE");
        boolean emailChanged = !email.equals(target.getEmail());
        if (emailChanged) {
            if (actorId.equals(target.getId())
                    || access.isAdminEmail(target.getEmail())
                    || access.isAdminEmail(email)) throw conflict("ADMIN_ACCOUNT_PROTECTED");
            if (users.findByEmail(email)
                    .filter(existing -> !existing.getId().equals(target.getId()))
                    .isPresent()) throw conflict("EMAIL_EXISTS");
            target.changeEmail(email);
            revokeAfterCommit(target);
        }
        target.updateName(name);
        users.save(target);
        if (Boolean.TRUE.equals(request.removeAvatar())) avatars.setAvatar(target.getId(), null);
        auditLogs.save(
                AdminAuditEntity.create(
                        actorId,
                        actor.getEmail(),
                        "USER_PROFILE_UPDATED",
                        target.getEmail(),
                        reason,
                        "SUCCESS"));
        return userDto(target);
    }

    private void revokeAfterCommit(UserEntity target) {
        org.springframework.transaction.support.TransactionSynchronizationManager
                .registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                try {
                                    revokeSessions(target);
                                } catch (RuntimeException exception) {
                                    log.warn("Disabled account session cleanup unavailable");
                                }
                            }
                        });
    }

    public AdminDtos.Revocation revoke(
            Long actorId, String id, String sessionId, AdminDtos.Reason request) {
        var actor = access.requireAdmin(actorId);
        var target = target(id);
        String reason = reason(request.reason());
        // Persist the intent before touching Redis; an unavailable audit store prevents the
        // operation.
        var audit =
                auditLogs.saveAndFlush(
                        AdminAuditEntity.create(
                                actorId,
                                actor.getEmail(),
                                "SESSION_REVOKED",
                                target.getEmail() + " / " + sessionId,
                                reason,
                                "PENDING"));
        try {
            boolean revoked = authSessions.revokeOwned(target.getId(), sessionId);
            if (revoked) notifications.revokeSession(target.getId(), sessionId);
            audit.complete(true);
            auditLogs.saveAndFlush(audit);
            return new AdminDtos.Revocation(revoked ? 1 : 0);
        } catch (RuntimeException exception) {
            audit.complete(false);
            auditLogs.saveAndFlush(audit);
            throw exception;
        }
    }

    public AdminDtos.Revocation revokeAll(Long actorId, String id, AdminDtos.Reason request) {
        var actor = access.requireAdmin(actorId);
        var target = target(id);
        var audit =
                auditLogs.saveAndFlush(
                        AdminAuditEntity.create(
                                actorId,
                                actor.getEmail(),
                                "SESSIONS_REVOKED",
                                target.getEmail(),
                                reason(request.reason()),
                                "PENDING"));
        try {
            int revoked = revokeSessions(target);
            audit.complete(true);
            auditLogs.saveAndFlush(audit);
            return new AdminDtos.Revocation(revoked);
        } catch (RuntimeException exception) {
            audit.complete(false);
            auditLogs.saveAndFlush(audit);
            throw exception;
        }
    }

    public AdminDtos.Settings settings(Long actorId) {
        access.requireAdmin(actorId);
        return settingsDto(
                settings.findByScopeAndIsDelFalse("ADMIN")
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.SERVICE_UNAVAILABLE,
                                                "ADMIN_SCHEMA_REQUIRED")));
    }

    @Transactional
    public AdminDtos.Settings updateSettings(Long actorId, AdminDtos.UpdateSettings request) {
        var actor = access.requireAdmin(actorId);
        var current =
                settings.findForUpdateByScope("ADMIN")
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.SERVICE_UNAVAILABLE,
                                                "ADMIN_SCHEMA_REQUIRED"));
        if (request.version() != current.getVersion()) throw conflict("SETTINGS_CHANGED");
        if (request.consoleName() == null
                || request.consoleName().isBlank()
                || request.consoleName().length() > 80
                || request.sessionWarningMinutes() < 1
                || request.sessionWarningMinutes() > 1440) throw bad("INVALID_SETTINGS");
        current.update(
                request.consoleName(),
                request.supportEmail() == null ? "" : request.supportEmail(),
                request.sessionWarningMinutes());
        settings.saveAndFlush(current);
        auditLogs.save(
                AdminAuditEntity.create(
                        actorId,
                        actor.getEmail(),
                        "SETTINGS_UPDATED",
                        "ADMIN",
                        reason(request.reason()),
                        "SUCCESS"));
        return settingsDto(current);
    }

    public AdminDtos.PageResult<AdminDtos.Audit> audit(Long actorId, int page, int size) {
        access.requireAdmin(actorId);
        var result = auditLogs.findByIsDelFalseOrderByCreatedAtDescIdDesc(pageRequest(page, size));
        var entries =
                result.getContent().stream()
                        .map(
                                entry ->
                                        new AdminDtos.Audit(
                                                hashids.encode(entry.getId()),
                                                entry.getActorLabel(),
                                                entry.getAction(),
                                                entry.getTargetLabel(),
                                                entry.getReason(),
                                                entry.getOutcome(),
                                                entry.getCreatedAt()))
                        .toList();
        return new AdminDtos.PageResult<>(
                entries, page, size, result.getTotalElements(), result.getTotalPages());
    }

    private int revokeSessions(UserEntity user) {
        int count = 0;
        for (var session : authSessions.listUserSessions(user.getId())) {
            if (authSessions.revokeOwned(user.getId(), session.id())) {
                count++;
                notifications.revokeSession(user.getId(), session.id());
            }
        }
        return count;
    }

    private AdminDtos.Settings settingsDto(AdminSettingsEntity value) {
        return new AdminDtos.Settings(
                value.getConsoleName(),
                value.getSupportEmail(),
                value.getSessionWarningMinutes(),
                value.getVersion(),
                List.of(
                        new AdminDtos.Provider("Vietmap", configured("maps.vietmap.api-key")),
                        new AdminDtos.Provider(
                                "SMTP", configured("MAIL_USER") && configured("MAIL_PASSWORD")),
                        new AdminDtos.Provider("Gemini", configured("GEMINI_API_KEY")),
                        new AdminDtos.Provider(
                                "Cloudinary",
                                configured("CLOUDINARY_CLOUD_NAME")
                                        && configured("CLOUDINARY_API_KEY")
                                        && configured("CLOUDINARY_API_SECRET"))));
    }

    private boolean configured(String property) {
        return !environment.getProperty(property, "").isBlank();
    }

    private AdminDtos.User userDto(UserEntity user) {
        return new AdminDtos.User(
                hashids.encode(user.getId()),
                user.getName(),
                user.getEmail(),
                user.getStatus(),
                user.getAvatarUrl(),
                user.hasPassword(),
                access.isAdminEmail(user.getEmail()),
                user.getPoliciesAcceptedAt(),
                user.getPoliciesVersion(),
                user.getPoliciesLocale(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getStatusBeforeDeletion());
    }

    private UserEntity target(String id) {
        return users.findById(hashids.decode(id)).orElseThrow(() -> missing("USER_NOT_FOUND"));
    }

    private PageRequest pageRequest(int page, int size) {
        if (page < 0 || page > 100000 || size < 1 || size > 100) throw bad("INVALID_PAGINATION");
        return PageRequest.of(page, size);
    }

    private String reason(String value) {
        String text = value == null ? "" : value.trim();
        if (text.length() < 3 || text.length() > 500) throw bad("REASON_REQUIRED");
        return text;
    }

    private ResponseStatusException bad(String code) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, code);
    }

    private ResponseStatusException missing(String code) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, code);
    }

    private ResponseStatusException conflict(String code) {
        return new ResponseStatusException(HttpStatus.CONFLICT, code);
    }
}
