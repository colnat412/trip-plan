package com.tripbudget.tripbudget_core.plantemplate.mappers;

import com.tripbudget.tripbudget_core.common.services.HashidsService;
import com.tripbudget.tripbudget_core.plantemplate.dtos.response.PlanTemplateCardResponse;
import com.tripbudget.tripbudget_core.plantemplate.repositories.PlanTemplateSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlanTemplateResponseMapper {
    private final HashidsService hashidsService;

    public PlanTemplateCardResponse toPublicCard(PlanTemplateSummary summary) {
        return toCard(summary, null);
    }

    public PlanTemplateCardResponse toOwnedCard(PlanTemplateSummary summary) {
        return toCard(summary, hashidsService.encode(summary.sourceTripId()));
    }

    private PlanTemplateCardResponse toCard(PlanTemplateSummary summary, String sourceTripId) {
        return new PlanTemplateCardResponse(
                hashidsService.encode(summary.id()), summary.title(), summary.destination(),
                summary.description(), summary.tags(), summary.durationDays(), summary.activityCount(),
                summary.estimatedCost(), summary.currency(), summary.status(), summary.contentVersion(),
                summary.authorName(), summary.usageCount(), sourceTripId
        );
    }
}
