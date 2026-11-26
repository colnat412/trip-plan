package com.tripbudget.tripbudget_core.settlement.dtos.response;

import com.tripbudget.tripbudget_core.trip.dtos.response.PageResponse;

public record SettlementWorkspaceResponse(
        TripSettlementSummaryResponse summary,
        PageResponse<MemberBalanceResponse> memberBalances,
        PageResponse<SuggestedSettlementResponse> suggestedSettlements,
        PageResponse<SettlementResponse> settlementHistory
) {}
