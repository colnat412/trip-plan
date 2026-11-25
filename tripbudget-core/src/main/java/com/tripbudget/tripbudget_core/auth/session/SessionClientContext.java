package com.tripbudget.tripbudget_core.auth.session;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class SessionClientContext {
    private final ObjectProvider<HttpServletRequest> requests;

    public SessionClientContext(ObjectProvider<HttpServletRequest> requests) { this.requests = requests; }

    public SessionClientInfo current() {
        try {
            HttpServletRequest request = requests.getIfAvailable();
            return request == null ? SessionClientInfo.unknown() : SessionClientInfo.from(
                    request.getHeader("User-Agent"), request.getHeader("X-Vivu-Client"), request.getServletPath());
        } catch (IllegalStateException exception) {
            return SessionClientInfo.unknown();
        }
    }
}
