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
                SELECT url FROM public.uploads
                WHERE id = ? AND user_id = ?
                  AND type = CAST(? AS public.upload_type_enum)
                  AND status = CAST(? AS public.upload_status_enum)
                """, (rs, row) -> rs.getString("url"),
                uploadId, currentUserId, purpose.name(), UploadStatus.PENDING.getDatabaseValue());
        return urls.stream().findFirst();
    }

    public Optional<String> claimPending(Long currentUserId, UUID uploadId, UploadPurpose purpose) {
        var urls = jdbc.query("""
                UPDATE public.uploads SET status = CAST(? AS public.upload_status_enum)
                WHERE id = ? AND user_id = ?
                  AND type = CAST(? AS public.upload_type_enum)
                  AND status = CAST(? AS public.upload_status_enum)
                RETURNING url
                """, (rs, row) -> rs.getString("url"),
                UploadStatus.ATTACHED.getDatabaseValue(), uploadId, currentUserId,
                purpose.name(), UploadStatus.PENDING.getDatabaseValue());
        return urls.stream().findFirst();
    }

    public void markDeletePending(Long currentUserId, UUID uploadId, UploadPurpose purpose) {
        jdbc.update("""
                UPDATE public.uploads SET status = CAST(? AS public.upload_status_enum)
                WHERE id = ? AND user_id = ?
                  AND type = CAST(? AS public.upload_type_enum)
                  AND status = CAST(? AS public.upload_status_enum)
                """, UploadStatus.DELETE_PENDING.getDatabaseValue(), uploadId, currentUserId,
                purpose.name(), UploadStatus.ATTACHED.getDatabaseValue());
    }

    public void releaseAttached(UUID uploadId, UploadPurpose purpose) {
        jdbc.update("""
                UPDATE public.uploads SET status = CAST(? AS public.upload_status_enum)
                WHERE id = ? AND type = CAST(? AS public.upload_type_enum)
                  AND status = CAST(? AS public.upload_status_enum)
                """, UploadStatus.DELETE_PENDING.getDatabaseValue(), uploadId, purpose.name(),
                UploadStatus.ATTACHED.getDatabaseValue());
    }
}
