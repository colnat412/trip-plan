package com.tripbudget.tripbudget_core.maps.services;

import com.tripbudget.tripbudget_core.maps.web.MapsException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

@Component
public class MapsBudget {

    private static final long WINDOW_MILLIS = 60_000;
    private static final int USER_REQUEST_LIMIT = 60;
    private static final int USER_ELEMENT_LIMIT = 2500;
    private static final int GLOBAL_REQUEST_LIMIT = 600;
    private static final int GLOBAL_ELEMENT_LIMIT = 25_000;
    private static final int MAX_TRACKED_USERS = 10_000;

    private final Clock clock;
    private final Map<Long, Budget> userBudgets = new HashMap<>();
    private Budget globalBudget = new Budget(0);

    private static class Budget {
        final long resetAt;
        int requests;
        int elements;

        Budget(long resetAt) {
            this.resetAt = resetAt;
        }
    }

    public MapsBudget() {
        this(Clock.systemUTC());
    }

    MapsBudget(Clock clock) {
        this.clock = clock;
    }

    public synchronized void consume(Long currentUserId, int elements) {
        long now = clock.millis();
        userBudgets.values().removeIf(budget -> budget.resetAt <= now);
        if (globalBudget.resetAt <= now) {
            globalBudget = new Budget(now + WINDOW_MILLIS);
        }
        Budget userBudget = userBudgets.getOrDefault(currentUserId, new Budget(now + WINDOW_MILLIS));
        boolean userLimitReached = userBudget.requests >= USER_REQUEST_LIMIT
                || userBudget.elements + elements > USER_ELEMENT_LIMIT;
        boolean globalLimitReached = globalBudget.requests >= GLOBAL_REQUEST_LIMIT
                || globalBudget.elements + elements > GLOBAL_ELEMENT_LIMIT;
        boolean cannotTrackNewUser = !userBudgets.containsKey(currentUserId)
                && userBudgets.size() >= MAX_TRACKED_USERS;
        if (userLimitReached || globalLimitReached || cannotTrackNewUser) {
            throw new MapsException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "MAPS_RATE_LIMITED",
                    "Too many map requests. Please try again in a minute."
            );
        }

        userBudget.requests++;
        userBudget.elements += elements;
        globalBudget.requests++;
        globalBudget.elements += elements;
        userBudgets.put(currentUserId, userBudget);
    }
}
