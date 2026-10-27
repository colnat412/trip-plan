package com.tripbudget.tripbudget_core.plantemplate.snapshot;

import com.tripbudget.tripbudget_core.plantemplate.dtos.TemplateSnapshot;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Currency;

@Component
public class TemplateSnapshotValidator {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_DAYS = 60;
    public static final int MAX_ACTIVITIES = 1_000;
    public static final int MAX_CHECKLIST_ITEMS = 200;
    public static final int MAX_SNAPSHOT_BYTES = 1_000_000;
    private static final BigDecimal MAX_ACTIVITY_COST = new BigDecimal("9999999999999.99");

    public void validate(TemplateSnapshot snapshot, String currency) {
        validateCurrency(currency);
        validateStructure(snapshot);

        int activityCount = 0;
        for (int index = 0; index < snapshot.days().size(); index++) {
            TemplateSnapshot.Day day = snapshot.days().get(index);
            validateDay(day, index + 1);
            for (TemplateSnapshot.Activity activity : day.activities()) {
                validateActivity(activity);
                activityCount++;
            }
        }
        if (activityCount < 1 || activityCount > MAX_ACTIVITIES) {
            throw invalidSnapshot();
        }
        for (TemplateSnapshot.Checklist checklist : snapshot.checklists()) {
            if (checklist == null || checklist.category() == null) {
                throw invalidSnapshot();
            }
            requireText(checklist.title(), 255);
        }
    }

    private void validateCurrency(String currency) {
        if (currency == null) {
            throw invalidSnapshot();
        }
        try {
            Currency.getInstance(currency);
        } catch (IllegalArgumentException exception) {
            throw invalidSnapshot();
        }
    }

    private void validateStructure(TemplateSnapshot snapshot) {
        if (snapshot == null || snapshot.schemaVersion() != SCHEMA_VERSION
                || snapshot.days() == null || snapshot.days().isEmpty()
                || snapshot.days().size() > MAX_DAYS || snapshot.checklists() == null
                || snapshot.checklists().size() > MAX_CHECKLIST_ITEMS) {
            throw invalidSnapshot();
        }
    }

    private void validateDay(TemplateSnapshot.Day day, int expectedDayNumber) {
        if (day == null || day.dayNumber() != expectedDayNumber || day.activities() == null) {
            throw invalidSnapshot();
        }
        validateTextLength(day.title(), 255);
    }

    private void validateActivity(TemplateSnapshot.Activity activity) {
        if (activity == null || activity.category() == null || activity.orderIndex() < 0) {
            throw invalidSnapshot();
        }
        BigDecimal estimatedCost = activity.estimatedCost();
        if (estimatedCost == null || estimatedCost.signum() < 0
                || estimatedCost.compareTo(MAX_ACTIVITY_COST) > 0 || estimatedCost.scale() > 2) {
            throw invalidSnapshot();
        }
        requireText(activity.title(), 255);
        validateTextLength(activity.location(), 255);
    }

    private void requireText(String value, int maximumLength) {
        if (value == null || value.isBlank()) {
            throw invalidSnapshot();
        }
        validateTextLength(value, maximumLength);
    }

    private void validateTextLength(String value, int maximumLength) {
        if (value != null && value.length() > maximumLength) {
            throw invalidSnapshot();
        }
    }

    private ResponseStatusException invalidSnapshot() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "INVALID_TEMPLATE_SNAPSHOT");
    }
}
