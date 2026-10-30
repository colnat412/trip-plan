package com.tripbudget.tripbudget_core.trip.services;

import com.tripbudget.tripbudget_core.notification.services.NotificationService;
import com.tripbudget.tripbudget_core.trip.dtos.request.InviteMemberRequest;
import com.tripbudget.tripbudget_core.trip.dtos.request.UpdateMemberRoleRequest;
import com.tripbudget.tripbudget_core.trip.dtos.response.TripMemberResponse;
import com.tripbudget.tripbudget_core.trip.entities.TripEntity;
import com.tripbudget.tripbudget_core.trip.entities.TripMemberEntity;
import com.tripbudget.tripbudget_core.trip.enums.TripMemberRole;
import com.tripbudget.tripbudget_core.trip.enums.TripMemberStatus;
import com.tripbudget.tripbudget_core.trip.repositories.TripMemberRepository;
import com.tripbudget.tripbudget_core.trip.repositories.TripRepository;
import com.tripbudget.tripbudget_core.user.entities.UserEntity;
import com.tripbudget.tripbudget_core.user.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.tripbudget.tripbudget_core.common.services.HashidsService;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TripMemberService {

    private final NotificationService notifications;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final UserRepository userRepository;
    private final HashidsService hashidsService;

    @Transactional(readOnly = true)
    public List<TripMemberResponse> getTripMembers(Long currentUserId, Long tripId) {
        getActiveTrip(tripId);
        getMemberOrThrow(tripId, currentUserId);

        List<TripMemberEntity> members = tripMemberRepository.findByTrip_IdAndIsDelFalseOrderByCreatedAtAsc(tripId);
        if (members.isEmpty()) {
            return List.of();
        }

        Set<Long> userIds = members.stream()
                .map(TripMemberEntity::getUserId)
                .collect(Collectors.toSet());

        List<UserEntity> users = userRepository.findAllByIdIn(userIds);
        Map<Long, UserEntity> userMap = users.stream()
                .collect(Collectors.toMap(UserEntity::getId, u -> u));

        return members.stream()
                .map(m -> mapToResponse(m, userMap.get(m.getUserId())))
                .toList();
    }

    @Transactional
    public TripMemberResponse inviteMember(Long currentUserId, Long tripId, InviteMemberRequest req) {
        TripEntity trip = getActiveTrip(tripId);
        TripMemberEntity caller = getMemberOrThrow(tripId, currentUserId);

        if (caller.getRole() != TripMemberRole.OWNER && caller.getRole() != TripMemberRole.VICE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only trip owner and vice can invite members");
        }

        if (req.role() == TripMemberRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot invite a member with OWNER role");
        }

        if (caller.getRole() != TripMemberRole.OWNER && (req.role() == TripMemberRole.VICE)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only trip owner can assign VICE role");
        }

        String searchEmail = req.email().trim().toLowerCase(Locale.ROOT);
        UserEntity targetUser = userRepository.findByEmail(searchEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User with email '" + req.email() + "' not found. Please ask them to register in TripBudget first."
                ));

        if (targetUser.getId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You are already a member of this trip");
        }

        TripMemberStatus initialStatus = caller.getRole() == TripMemberRole.OWNER ? TripMemberStatus.ACTIVE : TripMemberStatus.INVITED;

        Optional<TripMemberEntity> existingOpt = tripMemberRepository.findByTrip_IdAndUserId(tripId, targetUser.getId());
        if (existingOpt.isPresent()) {
            TripMemberEntity existing = existingOpt.get();
            if (!existing.isDel() && existing.getStatus() == TripMemberStatus.ACTIVE) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is already an active member of this trip");
            }
            if (!existing.isDel() && existing.getStatus() == TripMemberStatus.INVITED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User has already been invited to this trip");
            }

            existing.reactivate(req.role(), initialStatus);
            TripMemberEntity saved = tripMemberRepository.save(existing);
            notifications.memberAdded(currentUserId, trip, saved);
            return mapToResponse(saved, targetUser);
        }

        TripMemberEntity newMember = caller.getRole() == TripMemberRole.OWNER
                ? TripMemberEntity.addDirectMember(trip, targetUser.getId(), req.role())
                : TripMemberEntity.invite(trip, targetUser.getId(), req.role());
                
        TripMemberEntity saved = tripMemberRepository.save(newMember);
        notifications.memberAdded(currentUserId, trip, saved);
        return mapToResponse(saved, targetUser);
    }

    @Transactional
    public TripMemberResponse acceptMember(Long currentUserId, Long tripId, Long memberId) {
        getActiveTrip(tripId);
        TripMemberEntity caller = getMemberOrThrow(tripId, currentUserId);

        if (caller.getRole() != TripMemberRole.OWNER && caller.getRole() != TripMemberRole.VICE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the trip owner or vice can approve members");
        }

        TripMemberEntity targetMember = tripMemberRepository.findByIdAndTrip_IdAndIsDelFalse(memberId, tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip member not found"));

        if (caller.getRole() != TripMemberRole.OWNER && targetMember.getRole() != TripMemberRole.MEMBER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vice can only approve regular members");
        }
        if (targetMember.getStatus() != TripMemberStatus.INVITED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only pending invitations can be approved");
        }

        targetMember.updateStatus(TripMemberStatus.ACTIVE);
        TripMemberEntity saved = tripMemberRepository.save(targetMember);
        notifications.memberApproved(currentUserId, saved);

        UserEntity user = userRepository.findById(targetMember.getUserId()).orElse(null);
        return mapToResponse(saved, user);
    }

    @Transactional
    public TripMemberResponse updateMemberRole(Long currentUserId, Long tripId, Long memberId, UpdateMemberRoleRequest req) {
        getActiveTrip(tripId);
        TripMemberEntity caller = getMemberOrThrow(tripId, currentUserId);
        if (caller.getRole() != TripMemberRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the trip owner can change member roles");
        }
        TripMemberEntity targetMember = tripMemberRepository.findByIdAndTrip_IdAndIsDelFalse(memberId, tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip member not found"));
        if (targetMember.getRole() == TripMemberRole.OWNER || req.role() == TripMemberRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ownership transfer is not supported here");
        }

        targetMember.updateRole(req.role());
        TripMemberEntity saved = tripMemberRepository.save(targetMember);

        UserEntity user = userRepository.findById(targetMember.getUserId()).orElse(null);
        return mapToResponse(saved, user);
    }

    @Transactional
    public void removeMember(Long currentUserId, Long tripId, Long memberId) {
        getActiveTrip(tripId);
        TripMemberEntity caller = getMemberOrThrow(tripId, currentUserId);

        if (caller.getRole() != TripMemberRole.OWNER && caller.getRole() != TripMemberRole.VICE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the trip owner and vice can remove members");
        }

        TripMemberEntity targetMember = tripMemberRepository.findByIdAndTrip_IdAndIsDelFalse(memberId, tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip member not found"));

        if (targetMember.getRole() == TripMemberRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trip owner cannot be removed");
        }

        if (targetMember.getUserId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot remove yourself. Use leave trip instead.");
        }

        if (caller.getRole() != TripMemberRole.OWNER && (targetMember.getRole() == TripMemberRole.VICE)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vice cannot remove another Vice");
        }

        targetMember.remove();
        tripMemberRepository.save(targetMember);
    }

    @Transactional
    public void leaveTrip(Long currentUserId, Long tripId) {
        getActiveTrip(tripId);
        TripMemberEntity caller = getMemberOrThrow(tripId, currentUserId);

        if (caller.getRole() == TripMemberRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trip owner cannot leave the trip. Delete the trip instead.");
        }

        caller.leave();
        tripMemberRepository.save(caller);
    }

    private TripEntity getActiveTrip(Long tripId) {
        TripEntity trip = tripRepository.findActiveTrip(tripId);
        if (trip == null || trip.isDel()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
        }
        return trip;
    }

    private TripMemberEntity getMemberOrThrow(Long tripId, Long userId) {
        TripMemberEntity member = tripMemberRepository.findByTrip_IdAndUserIdAndIsDelFalse(tripId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission in this trip"));

        if (member.getStatus() != TripMemberStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your membership in this trip is not active");
        }

        return member;
    }

    private TripMemberResponse mapToResponse(TripMemberEntity member, UserEntity user) {
        String name = user != null ? user.getName() : "Unknown Member";
        String email = user != null ? user.getEmail() : "";
        String avatarUrl = user != null ? user.getAvatarUrl() : null;

        return new TripMemberResponse(
                hashidsService.encode(member.getId()),
                hashidsService.encode(member.getTrip().getId()),
                hashidsService.encode(member.getUserId()),
                name,
                email,
                avatarUrl,
                member.getRole(),
                member.getStatus(),
                member.getJoinedAt(),
                member.getCreatedAt()
        );
    }
}

