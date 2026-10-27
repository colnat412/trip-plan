package com.tripbudget.tripbudget_core.plantemplate.dtos.response;



public record PlanTemplateCopyResponse(
        String tripId, String sourceTemplateId, int sourceTemplateVersion
) {}
