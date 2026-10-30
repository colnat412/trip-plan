package com.tripbudget.tripbudget_core.notification.services;

import com.tripbudget.tripbudget_core.notification.entities.RealtimeOutboxEntity;
import com.tripbudget.tripbudget_core.notification.repositories.RealtimeOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OutboxStore {
    private final RealtimeOutboxRepository repository;

    @Transactional(readOnly = true)
    public List<RealtimeOutboxEntity> due() {
        return repository.findDue(Instant.now(), PageRequest.of(0, 50));
    }

    @Transactional
    public boolean claim(Long id, UUID token) {
        Instant now = Instant.now();
        return repository.claim(id, token, now, now.plusSeconds(60)) == 1;
    }

    @Transactional
    public void finish(Long id, UUID token, boolean success, int attempts) {
        Instant now = Instant.now();
        String status = success ? "DISPATCHED" : attempts >= 10 ? "FAILED" : "PENDING";
        repository.finish(id, token, status, now.plusSeconds(Math.min(300, 1L << Math.min(attempts, 8))),
                success ? now : null, now);
    }

    @Transactional
    public void cleanup() {
        var ids = repository.findDispatchedBefore(Instant.now().minusSeconds(7 * 86400L), PageRequest.of(0, 500));
        if (!ids.isEmpty()) repository.deleteAllByIdInBatch(ids);
    }
}
