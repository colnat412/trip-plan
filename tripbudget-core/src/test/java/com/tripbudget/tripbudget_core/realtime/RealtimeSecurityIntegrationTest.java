package com.tripbudget.tripbudget_core.realtime;

import com.tripbudget.tripbudget_core.security.*;
import com.tripbudget.tripbudget_core.user.repositories.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.*;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = RealtimeSecurityIntegrationTest.App.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.config.name=notification-websocket-test", "server.servlet.context-path=/api",
            "security.jwt.secret=notification-test-secret-with-at-least-thirty-two-characters",
            "app.web-origin=http://localhost:8080", "spring.main.banner-mode=off", "logging.level.org.springframework.web=INFO", "logging.level.org.springframework.messaging=INFO"})
@EnabledIfEnvironmentVariable(named = "NOTIFICATION_TEST_WEBSOCKET", matches = "true")
class RealtimeSecurityIntegrationTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
    @Import({SecurityConfig.class, RedisSessionValidationFilter.class, PolicyAcceptanceFilter.class,
        CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, RealtimeConfig.class,
        RealtimeMessageSecurity.class, RealtimeBootstrapController.class, RealtimeSessions.class, RealtimeCsrfHandshakeInterceptor.class,
        RealtimeSchedulingConfig.class})
    static class App {}

    @LocalServerPort int port;
    @MockitoBean StringRedisTemplate redis;
    @MockitoBean UserRepository users;
    @Autowired RealtimeSessions sessions;
    @Autowired ObjectMapper json;
    final List<WebSocketStompClient> clients = new ArrayList<>();
    ValueOperations<String, String> values;

    @BeforeEach @SuppressWarnings("unchecked") void authorize() {
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn("active-session");
        when(redis.hasKey(anyString())).thenReturn(true);
        when(users.hasAcceptedPolicies(anyLong())).thenReturn(true);
    }
    @AfterEach void close() { clients.forEach(WebSocketStompClient::stop); clients.clear(); }

    record Credentials(String cookies, String headerName, String csrf) {}
    String jwt(String subject) {
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                "notification-test-secret-with-at-least-thirty-two-characters".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var claims = JwtClaimsSet.builder().subject(subject).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300)).claim("sid", "session-" + subject).claim("type", "access").build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
    Credentials bootstrap(String subject) throws Exception {
        String access = "access_token=" + jwt(subject);
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/api/realtime/bootstrap"))
                .header("Cookie", access).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        var node = json.readTree(response.body()).get("data");
        String csrfCookie = response.headers().allValues("set-cookie").stream()
                .filter(cookie -> cookie.startsWith("WS-XSRF-TOKEN=")).reduce((first, last) -> last).orElseThrow().split(";", 2)[0];
        byte[] masked = Base64.getUrlDecoder().decode(node.get("token").asString());
        byte[] raw = new byte[masked.length / 2];
        for (int i = 0; i < raw.length; i++) raw[i] = (byte) (masked[i] ^ masked[i + raw.length]);
        assertTrue(new String(raw, java.nio.charset.StandardCharsets.UTF_8).equals(csrfCookie.substring(csrfCookie.indexOf('=') + 1)),
                "Bootstrap masked token must match its cookie");
        return new Credentials(access + "; " + csrfCookie, node.get("headerName").asString(), node.get("token").asString());
    }
    StompSession connect(Credentials credentials, String origin, boolean includeCsrf, StompSessionHandler handler) throws Exception {
        var client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new StringMessageConverter() {
            @Override protected boolean supportsMimeType(org.springframework.messaging.MessageHeaders headers) { return true; }
        });
        clients.add(client);
        var httpHeaders = new WebSocketHttpHeaders();
        httpHeaders.add("Cookie", credentials.cookies()); httpHeaders.add("Origin", origin);
        var headers = new StompHeaders();
        if (includeCsrf) headers.add(credentials.headerName(), credentials.csrf());
        return client.connectAsync("ws://127.0.0.1:" + port + "/api/ws", httpHeaders, headers, handler).get(5, TimeUnit.SECONDS);
    }

    @Test void cookieBootstrapCsrfConnectAndPrivateDeliveryWorkTogether() throws Exception {
        var lan = connect(bootstrap("3"), "http://localhost:8080", true, new StompSessionHandlerAdapter() {});
        var nam = connect(bootstrap("1"), "http://localhost:8080", true, new StompSessionHandlerAdapter() {});
        BlockingQueue<String> lanEvents = new LinkedBlockingQueue<>();
        BlockingQueue<String> namEvents = new LinkedBlockingQueue<>();
        lan.subscribe("/user/queue/notifications", frame(lanEvents));
        nam.subscribe("/user/queue/notifications", frame(namEvents));
        // Allow asynchronous SUBSCRIBE processing; retry signals, not application writes.
        for (int attempt = 0; attempt < 20 && lanEvents.isEmpty(); attempt++) {
            sessions.publish(3L, UUID.randomUUID(), "notification.created");
            if (lanEvents.poll(100, TimeUnit.MILLISECONDS) != null) { lanEvents.add("received"); break; }
        }
        assertNotNull(lanEvents.poll(1, TimeUnit.SECONDS));
        assertNull(namEvents.poll(100, TimeUnit.MILLISECONDS));
        when(redis.hasKey("auth:session:session-3")).thenReturn(false);
        sessions.publish(3L, UUID.randomUUID(), "notification.created");
        assertNull(lanEvents.poll(150, TimeUnit.MILLISECONDS));
    }

    private StompFrameHandler frame(BlockingQueue<String> events) {
        return new StompFrameHandler() {
            public Type getPayloadType(StompHeaders headers) { return String.class; }
            public void handleFrame(StompHeaders headers, Object payload) { events.add((String) payload); }
        };
    }

    @Test void missingCsrfAndForeignOriginCannotEstablishStompSession() throws Exception {
        var credentials = bootstrap("3");
        assertThrows(Exception.class, () -> connect(credentials, "http://localhost:8080", false, new StompSessionHandlerAdapter() {}));
        assertThrows(Exception.class, () -> connect(credentials, "https://foreign.example", true, new StompSessionHandlerAdapter() {}));
    }

    @Test void forbiddenSubscriptionAndForgedServerMessageAreRejected() throws Exception {
        for (boolean send : List.of(false, true)) {
            BlockingQueue<String> errors = new LinkedBlockingQueue<>();
            var session = connect(bootstrap("3"), "http://localhost:8080", true, new StompSessionHandlerAdapter() {
                @Override public Type getPayloadType(StompHeaders headers) { return String.class; }
                @Override public void handleFrame(StompHeaders headers, Object payload) { errors.add("denied"); }
            });
            if (send) session.send("/queue/notifications", "forged");
            else session.subscribe("/user/1/queue/notifications", frame(new LinkedBlockingQueue<>()));
            assertNotNull(errors.poll(3, TimeUnit.SECONDS));
        }
    }

    @Test void bootstrapRejectsMissingOrRevokedLogin() throws Exception {
        var http = HttpClient.newHttpClient();
        var uri = URI.create("http://127.0.0.1:" + port + "/api/realtime/bootstrap");
        assertEquals(401, http.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        when(values.get(anyString())).thenReturn(null);
        assertEquals(401, http.send(HttpRequest.newBuilder(uri).header("Cookie", "access_token=" + jwt("3")).GET().build(),
                HttpResponse.BodyHandlers.ofString()).statusCode());
    }
}
