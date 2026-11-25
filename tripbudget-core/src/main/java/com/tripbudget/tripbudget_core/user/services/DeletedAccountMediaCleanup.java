package com.tripbudget.tripbudget_core.user.services;

import com.tripbudget.tripbudget_core.uploads.clients.CloudinaryClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeletedAccountMediaCleanup {
    private final JdbcTemplate jdbc;
    private final CloudinaryClient cloudinary;

    @Scheduled(scheduler = "accountMediaScheduler", fixedDelayString = "${account.media-cleanup-delay-ms:60000}", initialDelay = 60000)
    public void cleanup() {
        try {
            var ids = jdbc.queryForList("SELECT public_id FROM trip_core.deleted_account_media ORDER BY created_at LIMIT 20", String.class);
            for (String id : ids) {
                try {
                    cloudinary.delete(id);
                    jdbc.update("DELETE FROM trip_core.deleted_account_media WHERE public_id = ?", id);
                } catch (RuntimeException error) { log.warn("Deleted account media cleanup deferred"); }
            }
        } catch (RuntimeException error) { log.warn("Deleted account media queue unavailable"); }
    }
}
