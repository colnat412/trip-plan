package com.tripbudget.tripbudget_core.uploads.services;

import com.tripbudget.tripbudget_core.uploads.clients.CloudinaryClient;
import com.tripbudget.tripbudget_core.uploads.dtos.response.UploadResponse;
import com.tripbudget.tripbudget_core.uploads.enums.UploadPurpose;
import com.tripbudget.tripbudget_core.uploads.enums.UploadStatus;
import com.tripbudget.tripbudget_core.uploads.repositories.UploadAsset;
import com.tripbudget.tripbudget_core.uploads.repositories.UploadRepository;
import com.tripbudget.tripbudget_core.user.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UploadService {
    private static final long MAX_BYTES = 5 * 1024 * 1024;
    private final CloudinaryClient cloudinary;
    private final UploadRepository uploads;
    private final UserService users;

    public UploadResponse uploadImage(Long currentUserId, MultipartFile file, UploadPurpose purpose) {
        users.authenticatedUser(currentUserId);
        if (purpose == null || file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid image or purpose");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File too large");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid image");
        }
        if (!isSupportedImage(bytes)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid image");
        }
        UUID uploadId = UUID.randomUUID();
        String prefix = switch (purpose) {
            case PROFILE_AVATAR -> "tripbudget/profile-avatars";
            case TRIP_COVER -> "tripbudget/trip-covers";
            case ACTIVITY_IMAGE -> "tripbudget/activity-images";
            case EXPENSE_RECEIPT -> "tripbudget/expense-receipts";
        };
        var result = cloudinary.upload(bytes, prefix + "/" + uploadId);
        var asset = new UploadAsset(uploadId, currentUserId, purpose.name(), result.publicId(), result.secureUrl(),
                result.resourceType(), result.format(), result.bytes(), result.width(), result.height(),
                UploadStatus.PENDING.getDatabaseValue());
        try {
            uploads.insert(asset);
        } catch (RuntimeException exception) {
            try {
                cloudinary.delete(result.publicId());
            } catch (RuntimeException cleanupFailure) {
                log.error("Upload cleanup failed; reconciliation required: uploadId={}, publicId={}", uploadId, result.publicId());
            }
            throw exception;
        }
        return new UploadResponse(asset.id(), asset.publicId(), asset.url(), asset.resourceType(), asset.format(),
                asset.bytes(), asset.width(), asset.height(), asset.purpose(), asset.status());
    }

    private boolean isSupportedImage(byte[] bytes) {
        boolean jpeg = bytes.length >= 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff;
        byte[] pngSignature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        boolean png = bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8), pngSignature);
        boolean webp = bytes.length >= 12 && "RIFF".equals(new String(bytes, 0, 4, StandardCharsets.US_ASCII))
                && "WEBP".equals(new String(bytes, 8, 4, StandardCharsets.US_ASCII));
        return jpeg || png || webp;
    }
}
