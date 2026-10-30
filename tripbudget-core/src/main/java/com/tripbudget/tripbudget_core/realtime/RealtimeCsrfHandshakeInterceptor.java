package com.tripbudget.tripbudget_core.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.*;
import org.springframework.security.web.csrf.*;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import java.util.Map;

/** Pin the cookie repository token, not a lazily generated request token, to this socket. */
@Component
@RequiredArgsConstructor
public class RealtimeCsrfHandshakeInterceptor implements HandshakeInterceptor {
    private final CookieCsrfTokenRepository tokens;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler handler, Map<String, Object> attributes) {
        var servlet = ((ServletServerHttpRequest) request).getServletRequest();
        var token = tokens.loadToken(servlet);
        if (token == null) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        attributes.put(CsrfToken.class.getName(), new DefaultCsrfToken(
                token.getHeaderName(), token.getParameterName(), token.getToken()));
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler handler, Exception error) { }
}
