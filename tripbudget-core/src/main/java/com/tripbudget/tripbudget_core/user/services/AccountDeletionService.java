package com.tripbudget.tripbudget_core.user.services;

import com.tripbudget.tripbudget_core.auth.session.AuthSessionService;
import com.tripbudget.tripbudget_core.user.dtos.request.DeleteAccountRequest;
import com.tripbudget.tripbudget_core.user.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AccountDeletionService {
    private final UserRepository users;
    private final AuthSessionService sessions;
    private final NamedParameterJdbcTemplate jdbc;

    @Transactional
    public void delete(Long userId, DeleteAccountRequest request) {
        var user = users.findForUpdateById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!"ACTIVE".equals(user.getStatus())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (!Boolean.TRUE.equals(request.confirmed()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_CONFIRMATION");
        var active = sessions.listUserSessions(userId);
        // Revoke before deletion: failure rolls back SQL and leaves a retryable account.
        for (var session : active) sessions.revokeForAccountDeletion(userId, session.id());
        var params = Map.<String, Object>of("id", userId, "email", user.getEmail());
        // Lock affected trips in deterministic order before removing shared content.
        jdbc.queryForList("SELECT id FROM trip_core.trips WHERE owner_id = :id OR id IN (SELECT trip_id FROM trip_core.trip_members WHERE user_id = :id) ORDER BY id FOR UPDATE", params);
        String owned = "SELECT id FROM trip_core.trips WHERE owner_id = :id";
        String activities = "SELECT id FROM trip_core.plan_activities WHERE created_by = :id OR trip_id IN (" + owned + ")";
        String expenses = "SELECT id FROM trip_core.expenses WHERE payer_id = :id OR created_by = :id OR trip_id IN (" + owned + ") OR activity_id IN (" + activities + ") OR id IN (SELECT expense_id FROM trip_core.expense_splits WHERE user_id = :id)";
        String settlements = "SELECT id FROM trip_core.settlements WHERE payer_id = :id OR payee_id = :id OR created_by = :id OR trip_id IN (" + owned + ")";
        String templates = "SELECT id FROM trip_core.plan_templates WHERE author_id = :id OR source_trip_id IN (" + owned + ")";
        // Queue remote deletion in the same transaction, so failed Cloudinary requests can retry.
        jdbc.update("INSERT INTO trip_core.deleted_account_media(public_id) SELECT public_id FROM trip_core.uploads WHERE user_id = :id ON CONFLICT DO NOTHING", params);
        jdbc.update("UPDATE trip_core.users SET avatar_upload_id = NULL, avatar_url = NULL WHERE avatar_upload_id IN (SELECT id FROM trip_core.uploads WHERE user_id = :id)", params);
        jdbc.update("UPDATE trip_core.trips SET cover_upload_id = NULL, cover_url = NULL WHERE cover_upload_id IN (SELECT id FROM trip_core.uploads WHERE user_id = :id)", params);
        jdbc.update("UPDATE trip_core.plan_templates SET cover_upload_id = NULL, cover_url = NULL WHERE cover_upload_id IN (SELECT id FROM trip_core.uploads WHERE user_id = :id)", params);
        jdbc.update("UPDATE trip_core.plan_activities SET image_upload_id = NULL, image_url = NULL WHERE image_upload_id IN (SELECT id FROM trip_core.uploads WHERE user_id = :id)", params);
        jdbc.update("UPDATE trip_core.expenses SET receipt_upload_id = NULL, receipt_url = NULL WHERE receipt_upload_id IN (SELECT id FROM trip_core.uploads WHERE user_id = :id)", params);
        jdbc.update("DELETE FROM trip_core.notifications WHERE recipient_id = :id OR trip_id IN (" + owned + ") OR (type LIKE 'MEMBER_%' AND target_id IN (SELECT id FROM trip_core.trip_members WHERE user_id = :id)) OR (type LIKE 'EXPENSE_%' AND target_id IN (" + expenses + ")) OR (type LIKE 'SETTLEMENT_%' AND target_id IN (" + settlements + ")) OR (type = 'ACTIVITY_REMINDER' AND target_id IN (" + activities + "))", params);
        jdbc.update("DELETE FROM trip_core.notification_deliveries WHERE device_id IN (SELECT id FROM trip_core.push_devices WHERE user_id = :id)", params);
        jdbc.update("DELETE FROM trip_core.push_devices WHERE user_id = :id", params);
        jdbc.update("DELETE FROM trip_core.notification_preferences WHERE user_id = :id", params);
        jdbc.update("DELETE FROM trip_core.plan_template_copies WHERE user_id = :id OR template_id IN (" + templates + ") OR new_trip_id IN (" + owned + ")", params);
        jdbc.update("DELETE FROM trip_core.plan_template_saves WHERE user_id = :id", params);
        jdbc.update("DELETE FROM trip_core.plan_templates WHERE id IN (" + templates + ")", params);
        // Remove complete shared financial entries rather than leaving incomplete splits.
        jdbc.update("UPDATE trip_core.plan_activities SET expense_id = NULL WHERE expense_id IN (" + expenses + ")", params);
        jdbc.update("DELETE FROM trip_core.expenses WHERE id IN (" + expenses + ")", params);
        jdbc.update("UPDATE trip_core.expenses SET activity_id = NULL WHERE activity_id IN (" + activities + ")", params);
        jdbc.update("DELETE FROM trip_core.settlements WHERE id IN (" + settlements + ")", params);
        jdbc.update("DELETE FROM trip_core.plan_activity_logs WHERE user_id = :id OR activity_id IN (" + activities + ")", params);
        jdbc.update("DELETE FROM trip_core.plan_activities WHERE id IN (" + activities + ")", params);
        jdbc.update("DELETE FROM trip_core.plan_checklists WHERE assignee_id = :id", params);
        jdbc.update("UPDATE trip_core.trip_members SET invited_by = NULL WHERE invited_by = :id", params);
        jdbc.update("DELETE FROM trip_core.trip_members WHERE user_id = :id", params);
        jdbc.update("DELETE FROM trip_core.trips WHERE owner_id = :id", params);
        jdbc.update("DELETE FROM trip_core.login_sessions WHERE user_id = :id", params);
        jdbc.update("DELETE FROM trip_core.admin_audit_logs WHERE actor_id = :id OR actor_label = :email OR target_label = :email", params);
        jdbc.update("DELETE FROM trip_core.uploads WHERE user_id = :id", params);
        jdbc.update("DELETE FROM trip_core.users WHERE id = :id", params);
    }
}
