package com.tripbudget.tripbudget_core.realtime;

import org.springframework.context.annotation.*;
import org.springframework.messaging.Message;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

@Configuration
@EnableWebSocketSecurity
public class RealtimeMessageSecurity {
    @Bean
    AuthorizationManager<Message<?>> realtimeAuthorization(
            MessageMatcherDelegatingAuthorizationManager.Builder messages) {
        return messages.nullDestMatcher().authenticated()
                .simpSubscribeDestMatchers("/user/queue/notifications").authenticated()
                .anyMessage().denyAll().build();
    }
}
