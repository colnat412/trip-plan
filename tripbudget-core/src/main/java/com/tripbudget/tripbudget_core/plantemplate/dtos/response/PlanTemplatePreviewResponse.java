package com.tripbudget.tripbudget_core.plantemplate.dtos.response;

import com.tripbudget.tripbudget_core.plantemplate.dtos.TemplateSnapshot;

public record PlanTemplatePreviewResponse(
        TemplateSnapshot snapshot, String sourceFingerprint, int contentVersion
) {}
