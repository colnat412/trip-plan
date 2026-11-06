package com.tripbudget.tripbudget_core.user.services;

import com.tripbudget.tripbudget_core.common.services.HashidsService;
import com.tripbudget.tripbudget_core.user.dtos.request.AcceptPoliciesRequest;
import com.tripbudget.tripbudget_core.user.dtos.response.UserProfileResponse;
import com.tripbudget.tripbudget_core.user.entities.UserEntity;
import com.tripbudget.tripbudget_core.user.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService {
    public static final String POLICY_VERSION = "2026-10-14";
    private final UserRepository users;
    private final HashidsService hashids;

    public UserEntity authenticatedUser(Long currentUserId) {
        return users.findById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    public UserProfileResponse getProfile(Long currentUserId) {
        return profile(authenticatedUser(currentUserId));
    }

    public UserProfileResponse profile(UserEntity user) {
        return new UserProfileResponse(hashids.encode(user.getId()), user.getEmail(), user.getName(),
                user.getAvatarUrl(), user.getPoliciesAcceptedAt(), user.getPoliciesVersion(), POLICY_VERSION);
    }

    @Transactional
    public UserProfileResponse updateProfile(Long currentUserId, String name) {
        UserEntity user = authenticatedUser(currentUserId);
        user.updateName(name);
        return profile(users.save(user));
    }

    @Transactional
    public UserProfileResponse acceptPolicies(Long currentUserId, AcceptPoliciesRequest request) {
        if (!Boolean.TRUE.equals(request.termsAccepted()) || !Boolean.TRUE.equals(request.contentAccepted())
                || !Boolean.TRUE.equals(request.privacyAccepted()) || !POLICY_VERSION.equals(request.version())
                || request.locale() == null || !Set.of("vi", "en").contains(request.locale())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "All current policies must be accepted");
        }
        authenticatedUser(currentUserId);
        users.acceptPoliciesIfAbsent(currentUserId, Instant.now(), POLICY_VERSION, request.locale());
        return getProfile(currentUserId);
    }
}
