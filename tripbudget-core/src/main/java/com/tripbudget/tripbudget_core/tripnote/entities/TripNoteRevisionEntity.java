package com.tripbudget.tripbudget_core.tripnote.entities;

import com.tripbudget.tripbudget_core.common.entities.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Getter
@Entity
@Table(schema = "trip_core", name = "trip_note_revisions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripNoteRevisionEntity extends BaseEntity {
    @Column(name = "note_id", nullable = false, updatable = false)
    private Long noteId;
    @Column(name = "editor_id", nullable = false, updatable = false)
    private Long editorId;
    @Column(name = "note_version", nullable = false, updatable = false)
    private long noteVersion;
    @Column(nullable = false, length = 2000, updatable = false)
    private String content;
    @Column(name = "image_upload_id", updatable = false)
    private UUID imageUploadId;
    @Column(name = "image_url", length = 1000, updatable = false)
    private String imageUrl;
    @Column(nullable = false, updatable = false)
    private boolean baseline;

    public static TripNoteRevisionEntity snapshot(TripNoteEntity note, Long editorId) {
        TripNoteRevisionEntity revision = new TripNoteRevisionEntity();
        revision.noteId = note.getId();
        revision.editorId = editorId;
        revision.noteVersion = note.getVersion();
        revision.content = note.getContent();
        revision.imageUploadId = note.getImageUploadId();
        revision.imageUrl = note.getImageUrl();
        return revision;
    }
}
