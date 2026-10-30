package com.tripbudget.tripbudget_core.realtime;

import com.tripbudget.tripbudget_core.user.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class RealtimeSessions {
    private final StringRedisTemplate redis;
    private final UserRepository users;
    private final ObjectProvider<SimpMessagingTemplate> messaging;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void register(WebSocketSession session) {
        if (validate(session)) sessions.put(session.getId(), session);
    }

    public void remove(String id) { sessions.remove(id); }

    public boolean validate(WebSocketSession session) {
        try {
            if (session.isOpen() && session.getPrincipal() instanceof JwtAuthenticationToken authentication) {
                var jwt = authentication.getToken();
                var expires = jwt.getExpiresAt();
                String sid = jwt.getClaimAsString("sid");
                if (expires != null && expires.isAfter(Instant.now()) && sid != null
                        && "access".equals(jwt.getClaimAsString("type"))
                        && Boolean.TRUE.equals(redis.hasKey("auth:session:" + sid))
                        && users.hasAcceptedPolicies(Long.valueOf(authentication.getName()))) return true;
            }
        } catch (RuntimeException ignored) {
            // A failed authority/session check must not leave a private stream open.
        }
        sessions.remove(session.getId());
        try { session.close(CloseStatus.POLICY_VIOLATION); } catch (java.io.IOException ignored) { }
        return false;
    }

    public record Signal(int schemaVersion, UUID eventId, String type) {}

    public void publish(Long userId, UUID eventId, String type) {
        for (WebSocketSession session : sessions.values()) {
            if (session.getPrincipal() == null || !userId.toString().equals(session.getPrincipal().getName())) continue;
            if (!validate(session)) continue;
            var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
            headers.setSessionId(session.getId());
            headers.setLeaveMutable(true);
            messaging.getObject().convertAndSendToUser(userId.toString(), "/queue/notifications",
                    new Signal(1, eventId, type), headers.getMessageHeaders());
        }
    }

    @Scheduled(fixedDelay = 15_000)
    public void expireSessions() { sessions.values().forEach(this::validate); }
}
