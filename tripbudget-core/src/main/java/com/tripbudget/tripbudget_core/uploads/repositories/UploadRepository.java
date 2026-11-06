package com.tripbudget.tripbudget_core.uploads.repositories;

import com.tripbudget.tripbudget_core.uploads.enums.UploadPurpose;
import com.tripbudget.tripbudget_core.uploads.enums.UploadStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class UploadRepository {
    private final JdbcTemplate jdbc;

    public UploadRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> findPendingUrl(Long currentUserId, UUID uploadId, UploadPurpose purpose) {
        var urls = jdbc.query("""
                SELECT url FROM trip_core.uploads
                WHERE id = ? AND user_id = ?
                  AND type = CAST(? AS trip_core.upload_type_enum)
                  AND status = CAST(? AS trip_core.upload_status_enum)
                """, (rs, row) -> rs.getString("url"),
                uploadId, currentUserId, purpose.name(), UploadStatus.PENDING.getDatabaseValue());
        return urls.stream().findFirst();
    }

    public Optional<String> claimPending(Long currentUserId, UUID uploadId, UploadPurpose purpose) {
        var urls = jdbc.query("""
                UPDATE trip_core.uploads SET status = CAST(? AS trip_core.upload_status_enum)
                WHERE id = ? AND user_id = ?
                  AND type = CAST(? AS trip_core.upload_type_enum)
                  AND status = CAST(? AS trip_core.upload_status_enum)
                RETURNING url
                """, (rs, row) -> rs.getString("url"),
                UploadStatus.ATTACHED.getDatabaseValue(), uploadId, currentUserId,
                purpose.name(), UploadStatus.PENDING.getDatabaseValue());
        return urls.stream().findFirst();
    }

    public void markDeletePending(Long currentUserId, UUID uploadId, UploadPurpose purpose) {
        jdbc.update("""
                UPDATE trip_core.uploads SET status = CAST(? AS trip_core.upload_status_enum)
                WHERE id = ? AND user_id = ?
                  AND type = CAST(? AS trip_core.upload_type_enum)
                  AND status = CAST(? AS trip_core.upload_status_enum)
                """, UploadStatus.DELETE_PENDING.getDatabaseValue(), uploadId, currentUserId,
                purpose.name(), UploadStatus.ATTACHED.getDatabaseValue());
    }

    public void releaseAttached(UUID uploadId, UploadPurpose purpose) {
        jdbc.update("""
                UPDATE trip_core.uploads SET status = CAST(? AS trip_core.upload_status_enum)
                WHERE id = ? AND type = CAST(? AS trip_core.upload_type_enum)
                  AND status = CAST(? AS trip_core.upload_status_enum)
                """, UploadStatus.DELETE_PENDING.getDatabaseValue(), uploadId, purpose.name(),
                UploadStatus.ATTACHED.getDatabaseValue());
    }
    public void insert(UploadAsset asset) {
        jdbc.update("""
                INSERT INTO trip_core.uploads
                    (id, user_id, type, public_id, url, resource_type, format, bytes, width, height, status)
                VALUES (?, ?, CAST(? AS trip_core.upload_type_enum), ?, ?, ?, ?, ?, ?, ?, CAST(? AS trip_core.upload_status_enum))
                """, asset.id(), asset.userId(), asset.purpose(), asset.publicId(), asset.url(),
                asset.resourceType(), asset.format(), asset.bytes(), asset.width(), asset.height(), asset.status());
    }

    public Optional<UploadAsset> findForUpdate(Long currentUserId, UUID uploadId, UploadPurpose purpose) {
        var assets = jdbc.query("""
                SELECT id, user_id, type, public_id, url, resource_type, format, bytes, width, height, status
                FROM trip_core.uploads
                WHERE id = ? AND user_id = ? AND type = CAST(? AS trip_core.upload_type_enum)
                FOR UPDATE
                """, (rs, row) -> new UploadAsset(rs.getObject("id", UUID.class), rs.getLong("user_id"),
                rs.getString("type"), rs.getString("public_id"), rs.getString("url"),
                rs.getString("resource_type"), rs.getString("format"), rs.getObject("bytes", Integer.class),
                rs.getObject("width", Integer.class), rs.getObject("height", Integer.class), rs.getString("status")),
                uploadId, currentUserId, purpose.name());
        return assets.stream().findFirst();
    }

}
