package com.tripbudget.tripbudget_core.user.services;

import com.tripbudget.tripbudget_core.uploads.enums.UploadPurpose;
import com.tripbudget.tripbudget_core.uploads.enums.UploadStatus;
import com.tripbudget.tripbudget_core.uploads.repositories.UploadAsset;
import com.tripbudget.tripbudget_core.uploads.repositories.UploadRepository;
import com.tripbudget.tripbudget_core.user.dtos.response.UserProfileResponse;
import com.tripbudget.tripbudget_core.user.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserAvatarService {
    private final UserRepository users;
    private final UploadRepository uploads;
    private final UserService profiles;

    @Transactional
    public UserProfileResponse setAvatar(Long currentUserId, UUID uploadId) {
        var user = users.findForUpdateById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        UploadAsset upload = null;
        if (uploadId != null) {
            upload = uploads.findForUpdate(currentUserId, uploadId, UploadPurpose.PROFILE_AVATAR)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Avatar upload not found"));
            if (uploadId.equals(user.getAvatarUploadId()) && UploadStatus.ATTACHED.getDatabaseValue().equals(upload.status())) {
                return profiles.profile(user);
            }
            if (!UploadStatus.PENDING.getDatabaseValue().equals(upload.status())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Upload is no longer available");
            }
        }
        UUID oldUploadId = user.getAvatarUploadId();
        user.updateAvatar(uploadId, upload == null ? null : upload.url());
        users.saveAndFlush(user);
        if (upload != null) {
            uploads.claimPending(currentUserId, uploadId, UploadPurpose.PROFILE_AVATAR)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Upload is no longer available"));
        }
        if (oldUploadId != null && !Objects.equals(oldUploadId, uploadId)) {
            uploads.markDeletePending(currentUserId, oldUploadId, UploadPurpose.PROFILE_AVATAR);
        }
        return profiles.profile(user);
    }
}
