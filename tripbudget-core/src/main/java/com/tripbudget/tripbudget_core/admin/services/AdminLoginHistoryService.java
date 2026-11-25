package com.tripbudget.tripbudget_core.admin.services;

import com.tripbudget.tripbudget_core.admin.access.AdminAccess;
import com.tripbudget.tripbudget_core.admin.dtos.AdminDtos;
import com.tripbudget.tripbudget_core.auth.repositories.LoginSessionRepository;
import com.tripbudget.tripbudget_core.common.services.HashidsService;
import com.tripbudget.tripbudget_core.user.entities.UserEntity;
import com.tripbudget.tripbudget_core.user.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminLoginHistoryService {
    private final AdminAccess access;
    private final LoginSessionRepository sessions;
    private final UserRepository users;
    private final HashidsService hashids;

    @Transactional(readOnly = true)
    public AdminDtos.PageResult<AdminDtos.RecentLogin> page(Long actorId, String query, int page) {
        access.requireAdmin(actorId);
        if (page < 0 || page > 100000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_PAGE");
        String term = query == null ? "" : query.trim();
        if (term.length() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_FILTER");
        String pattern = term.isEmpty() ? "" : "%" + term.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var result = sessions.searchForAdmin(pattern, PageRequest.of(page, 5));
        var userIds = result.getContent().stream().map(session -> session.getUserId()).distinct().toList();
        var accounts = users.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        var items = result.getContent().stream().map(session -> {
            var user = accounts.get(session.getUserId());
            return new AdminDtos.RecentLogin(session.getSessionId().toString(),
                    user == null ? null : hashids.encode(user.getId()),
                    user == null ? null : user.getName(), user == null ? null : user.getEmail(),
                    session.getClientType(), session.getBrowser(), session.getOperatingSystem(),
                    session.getDeviceType(), session.getLoginMethod(), session.getSignedInAt(),
                    "FAILED".equals(session.getStatus()) ? "FAILED" : "SUCCESS");
        }).toList();
        return new AdminDtos.PageResult<>(items, result.getNumber(), 5,
                result.getTotalElements(), result.getTotalPages());
    }
}
