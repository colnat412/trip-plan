package com.tripbudget.tripbudget_core.plantemplate.dtos.response;

import java.util.List;

public record PlanTemplateListResponse(
        List<PlanTemplateCardResponse> content, int number, int size, long totalElements, int totalPages
) {}
