package com.tripbudget.tripbudget_core.auth.services;

import com.tripbudget.tripbudget_core.auth.dtos.response.LoginSessionPage;
import com.tripbudget.tripbudget_core.auth.dtos.response.LoginSessionResponse;
import com.tripbudget.tripbudget_core.auth.entities.LoginSessionEntity;
import com.tripbudget.tripbudget_core.auth.repositories.LoginSessionRepository;
import com.tripbudget.tripbudget_core.auth.session.SessionClientInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class SessionHistoryService {
    private final LoginSessionRepository repository;

    public void record(Long userId, String id, Instant created, Instant expires, SessionClientInfo info) {
        repository.saveAndFlush(LoginSessionEntity.create(userId, UUID.fromString(id), created, expires, info));
    }

    public void importExisting(Long userId, String id, Instant created, Instant expires) {
        if (!repository.existsBySessionId(UUID.fromString(id)))
            repository.saveAndFlush(LoginSessionEntity.create(userId, UUID.fromString(id), created, expires, SessionClientInfo.unknown()));
    }

    public boolean exists(String id) { return repository.existsBySessionId(UUID.fromString(id)); }

    public void refreshed(Long userId, String id) {
        repository.findBySessionIdAndUserIdAndIsDelFalse(UUID.fromString(id), userId)
                .ifPresent(entity -> entity.refreshed(Instant.now()));
    }

    public void ended(Long userId, String id, String status) {
        repository.findBySessionIdAndUserIdAndIsDelFalse(UUID.fromString(id), userId)
                .ifPresent(entity -> entity.end(status, Instant.now()));
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public LoginSessionPage page(Long userId, String currentId, int page, int size, Set<String> activeIds) {
        if (page < 0 || size < 1 || size > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_PAGE");
        var result = repository.findByUserIdAndIsDelFalseOrderBySignedInAtDescIdDesc(userId, PageRequest.of(page, size));
        Instant now = Instant.now();
        var items = result.getContent().stream().map(entity -> {
            String sid = entity.getSessionId().toString();
            String status = activeIds.contains(sid) ? "ACTIVE" : !"ACTIVE".equals(entity.getStatus()) ? entity.getStatus()
                    : !entity.getExpiresAt().isAfter(now) ? "EXPIRED" : "ENDED";
            Instant endedAt = "EXPIRED".equals(status) ? entity.getExpiresAt() : entity.getEndedAt();
            return new LoginSessionResponse(sid, entity.getClientType(), entity.getBrowser(), entity.getOperatingSystem(),
                    entity.getDeviceType(), entity.getLoginMethod(), entity.getSignedInAt(), entity.getLastRefreshedAt(),
                    entity.getExpiresAt(), endedAt, status, "ACTIVE".equals(status) && sid.equals(currentId));
        }).toList();
        return new LoginSessionPage(items, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), activeIds.size(), currentId != null && activeIds.contains(currentId));
    }
}
