package com.tripbudget.tripbudget_core.auth.dtos.response;

import java.util.List;

public record LoginSessionPage(List<LoginSessionResponse> items, int page, int size,
        long totalElements, int totalPages, int activeCount, boolean currentSessionActive) {}
