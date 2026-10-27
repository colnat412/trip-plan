package com.tripbudget.tripbudget_core.plantemplate.repositories;

import org.springframework.data.domain.Pageable;

public record PlanTemplateSearchCriteria(
        Long authorId,
        String search,
        Integer durationDays,
        String tag,
        boolean popularFirst,
        Pageable pageable
) {}
