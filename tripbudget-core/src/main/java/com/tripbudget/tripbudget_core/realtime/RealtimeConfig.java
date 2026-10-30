package com.tripbudget.tripbudget_core.realtime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.CloseStatus;

@Configuration
@EnableWebSocketMessageBroker
public class RealtimeConfig implements WebSocketMessageBrokerConfigurer {
    private final String appOrigin;
    private final RealtimeSessions sessions;
    private final RealtimeCsrfHandshakeInterceptor csrfHandshake;
    private TaskScheduler heartbeatScheduler;

    public RealtimeConfig(@Value("${app.web-origin}") String appOrigin, RealtimeSessions sessions, RealtimeCsrfHandshakeInterceptor csrfHandshake) {
        this.csrfHandshake = csrfHandshake;
        this.sessions = sessions;
        this.appOrigin = appOrigin;
    }

    @Autowired
    public void setHeartbeatScheduler(
            @Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler scheduler) {
        this.heartbeatScheduler = scheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry endpoints) {
        endpoints.addEndpoint("/ws").setAllowedOrigins(appOrigin).addInterceptors(csrfHandshake);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration transport) {
        transport.setMessageSizeLimit(16 * 1024).setSendBufferSizeLimit(256 * 1024)
                .setSendTimeLimit(10_000).setTimeToFirstMessage(10_000);
        transport.addDecoratorFactory(handler -> new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                sessions.register(session);
                super.afterConnectionEstablished(session);
            }
            @Override
            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
                if (sessions.validate(session)) super.handleMessage(session, message);
            }
            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                sessions.remove(session.getId());
                super.afterConnectionClosed(session, status);
            }
        });
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry broker) {
        broker.setApplicationDestinationPrefixes("/app");
        broker.setUserDestinationPrefix("/user");
        broker.enableSimpleBroker("/queue")
                .setHeartbeatValue(new long[] {10_000, 10_000})
                .setTaskScheduler(heartbeatScheduler);
    }
}
