package com.tripbudget.tripbudget_core.admin.access;

import com.tripbudget.tripbudget_core.user.entities.UserEntity;
import com.tripbudget.tripbudget_core.user.repositories.UserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class AdminAccess {
    private final UserRepository users;
    private final Set<String> emails;

    public AdminAccess(UserRepository users, @Value("${ADMIN_EMAILS:}") String allowedEmails) {
        this.users = users;
        this.emails =
                Arrays.stream(allowedEmails.split(","))
                        .map(value -> value.trim().toLowerCase(Locale.ROOT))
                        .filter(value -> !value.isBlank())
                        .collect(Collectors.toUnmodifiableSet());
    }

    public UserEntity requireAdmin(Long actorId) {
        if (actorId == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        UserEntity actor =
                users.findById(actorId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED, "UNAUTHORIZED"));
        if (!"ACTIVE".equals(actor.getStatus()) || !isAdminEmail(actor.getEmail()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED");
        return actor;
    }

    public boolean isAdminEmail(String email) {
        return email != null && emails.contains(email.trim().toLowerCase(Locale.ROOT));
    }
}
