package com.tripbudget.tripbudget_core.user.dtos.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;

public record AcceptPoliciesRequest(
        Boolean termsAccepted, Boolean contentAccepted, Boolean privacyAccepted, String version, String locale
) {
    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Unexpected field: " + name);
    }
}
