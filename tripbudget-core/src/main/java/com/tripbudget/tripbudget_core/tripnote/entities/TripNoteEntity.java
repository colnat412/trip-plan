package com.tripbudget.tripbudget_core.tripnote.entities;

import com.tripbudget.tripbudget_core.common.entities.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Getter
@Entity
@Table(schema = "trip_core", name = "trip_notes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripNoteEntity extends BaseEntity {
    @Column(name = "trip_id", nullable = false, updatable = false)
    private Long tripId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private Long authorId;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(name = "image_upload_id")
    private UUID imageUploadId;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Version
    @Column(nullable = false)
    private long version;

    public static TripNoteEntity create(Long tripId, Long authorId, String content, UUID imageUploadId, String imageUrl) {
        TripNoteEntity note = new TripNoteEntity();
        note.tripId = tripId;
        note.authorId = authorId;
        note.updateContentAndImage(content, imageUploadId, imageUrl);
        return note;
    }

    public void updateContentAndImage(String content, UUID imageUploadId, String imageUrl) {
        String text = content == null ? "" : content.strip();
        if ((text.isBlank() && imageUrl == null) || text.length() > 2000) {
            throw new IllegalArgumentException("A note requires text or an image, with at most 2000 characters");
        }
        this.content = text;
        this.imageUploadId = imageUploadId;
        this.imageUrl = imageUrl;
    }
}
