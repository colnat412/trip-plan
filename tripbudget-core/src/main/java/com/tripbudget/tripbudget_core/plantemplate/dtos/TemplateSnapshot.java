package com.tripbudget.tripbudget_core.plantemplate.dtos;

import com.tripbudget.tripbudget_core.plan.enums.ActivityCategory;
import com.tripbudget.tripbudget_core.plan.enums.ChecklistCategory;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

public record TemplateSnapshot(int schemaVersion, List<Day> days, List<Checklist> checklists) {
    public record Day(
            int dayNumber, String title, String note, List<Activity> activities
    ) {}

    public record Activity(
            String title, LocalTime startTime, LocalTime endTime, String location,
            ActivityCategory category, BigDecimal estimatedCost, int orderIndex, String note
    ) {}

    public record Checklist(String title, ChecklistCategory category) {}
}
