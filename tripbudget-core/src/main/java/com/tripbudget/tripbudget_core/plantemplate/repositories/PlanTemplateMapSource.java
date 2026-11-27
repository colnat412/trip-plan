package com.tripbudget.tripbudget_core.plantemplate.repositories;

public record PlanTemplateMapSource(long id, String title, String destination,
    String coverUrl, int durationDays, long likeCount) {}
