package com.tripbudget.tripbudget_core.trip.services;

import com.tripbudget.tripbudget_core.trip.entities.TripMemberEntity;
import com.tripbudget.tripbudget_core.trip.enums.TripMemberStatus;
import com.tripbudget.tripbudget_core.trip.repositories.TripMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class TripPermissionService {
    private final TripMemberRepository members;

    public TripMemberEntity requireActiveMember(Long tripId, Long actorId) {
        var member = members.findByTrip_IdAndUserIdAndIsDelFalse(tripId, actorId)
                .orElseThrow(() -> forbidden());
        if (member.getStatus() != TripMemberStatus.ACTIVE) {
            throw forbidden();
        }
        return member;
    }

    public TripMemberEntity requireManager(Long tripId, Long actorId) {
        var member = requireActiveMember(tripId, actorId);
        if (!member.getRole().canManageTrip()) {
            throw forbidden();
        }
        return member;
    }

    public void requireManagerOrCreator(Long tripId, Long actorId, Long createdBy) {
        var member = requireActiveMember(tripId, actorId);
        if (!member.getRole().canManageTrip() && !actorId.equals(createdBy)) {
            throw forbidden();
        }
    }

    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission for this action");
    }
}
