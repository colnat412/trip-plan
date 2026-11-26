package com.tripbudget.tripbudget_core.tripnote.services;

import com.tripbudget.tripbudget_core.common.services.HashidsService;
import com.tripbudget.tripbudget_core.trip.dtos.response.PageResponse;
import com.tripbudget.tripbudget_core.trip.entities.TripEntity;
import com.tripbudget.tripbudget_core.trip.enums.TripStatus;
import com.tripbudget.tripbudget_core.trip.repositories.TripRepository;
import com.tripbudget.tripbudget_core.trip.services.TripPermissionService;
import com.tripbudget.tripbudget_core.tripnote.dtos.request.*;
import com.tripbudget.tripbudget_core.tripnote.dtos.response.*;
import com.tripbudget.tripbudget_core.tripnote.entities.TripNoteEntity;
import com.tripbudget.tripbudget_core.tripnote.entities.TripNoteRevisionEntity;
import com.tripbudget.tripbudget_core.tripnote.repositories.TripNoteRepository;
import com.tripbudget.tripbudget_core.tripnote.repositories.TripNoteRevisionRepository;
import com.tripbudget.tripbudget_core.uploads.services.UploadAttachmentService;
import com.tripbudget.tripbudget_core.uploads.enums.UploadPurpose;
import com.tripbudget.tripbudget_core.user.entities.UserEntity;
import com.tripbudget.tripbudget_core.user.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TripNoteService {
    private final TripNoteRepository notes;
    private final TripNoteRevisionRepository revisions;
    private final UploadAttachmentService uploads;
    private final TripRepository trips;
    private final TripPermissionService permissions;
    private final UserRepository users;
    private final HashidsService hashids;

    @Transactional(readOnly = true)
    public PageResponse<TripNoteResponse> list(Long actorId, Long tripId, int page) {
        requireTrip(tripId, false);
        var member = permissions.requireActiveMember(tripId, actorId);
        if (page < 0 || page > 5000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page");
        var result = notes.findByTripIdAndIsDelFalseOrderByCreatedAtDescIdDesc(tripId, PageRequest.of(page, 20));
        var authorIds = result.stream().map(TripNoteEntity::getAuthorId).distinct().toList();
        Map<Long, UserEntity> authors = users.findAllByIdIn(authorIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        return PageResponse.from(result.map(note -> response(note, authors.get(note.getAuthorId()), actorId, member.getRole().canManageTrip())));
    }

    @Transactional
    public TripNoteResponse create(Long actorId, Long tripId, CreateTripNoteRequest request) {
        requireTrip(tripId, true);
        var member = permissions.requireActiveMember(tripId, actorId);
        validateContent(request.content(), request.imageUploadId() != null);
        String imageUrl = request.imageUploadId() == null ? null
                : uploads.attachPending(actorId, request.imageUploadId(), UploadPurpose.TRIP_NOTE_IMAGE);
        var note = notes.saveAndFlush(TripNoteEntity.create(tripId, actorId, request.content(), request.imageUploadId(), imageUrl));
        revisions.save(TripNoteRevisionEntity.snapshot(note, actorId));
        return response(note, users.findById(actorId).orElse(null), actorId, member.getRole().canManageTrip());
    }

    @Transactional
    public TripNoteResponse update(Long actorId, Long tripId, Long noteId, UpdateTripNoteRequest request) {
        requireTrip(tripId, true);
        var member = permissions.requireActiveMember(tripId, actorId);
        var note = requireNote(tripId, noteId);
        if (!actorId.equals(note.getAuthorId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the author can edit this note");
        }
        if (request.version() == null || request.version() != note.getVersion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This note has changed; reload before editing");
        }
        if (request.imageUploadId() != null && request.removeImage()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot replace and remove image together");
        }
        UUID imageId = note.getImageUploadId();
        if (request.removeImage()) {
            imageId = null;
        } else if (request.imageUploadId() != null) {
            imageId = request.imageUploadId();
        }
        validateContent(request.content(), imageId != null);
        String content = request.content() == null ? "" : request.content().strip();
        if (Objects.equals(content, note.getContent()) && Objects.equals(imageId, note.getImageUploadId())) {
            return response(note, users.findById(note.getAuthorId()).orElse(null), actorId, member.getRole().canManageTrip());
        }
        String imageUrl = note.getImageUrl();
        if (imageId == null) {
            imageUrl = null;
        } else if (!Objects.equals(imageId, note.getImageUploadId())) {
            imageUrl = uploads.attachPending(actorId, imageId, UploadPurpose.TRIP_NOTE_IMAGE);
        }
        note.updateContentAndImage(content, imageId, imageUrl);
        notes.saveAndFlush(note);
        // Old images stay attached: immutable revisions must still be able to display them.
        revisions.save(TripNoteRevisionEntity.snapshot(note, actorId));
        return response(note, users.findById(note.getAuthorId()).orElse(null), actorId, member.getRole().canManageTrip());
    }

    @Transactional
    public void delete(Long actorId, Long tripId, Long noteId) {
        requireTrip(tripId, true);
        permissions.requireActiveMember(tripId, actorId);
        var note = requireNote(tripId, noteId);
        permissions.requireManagerOrCreator(tripId, actorId, note.getAuthorId());
        note.markDeleted();
        notes.save(note);
    }

    @Transactional(readOnly = true)
    public PageResponse<TripNoteRevisionResponse> history(Long actorId, Long tripId, Long noteId, int page) {
        requireTrip(tripId, false);
        permissions.requireActiveMember(tripId, actorId);
        if (page < 0 || page > 5000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page");
        if (!notes.existsByIdAndTripIdAndIsDelFalse(noteId, tripId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found");
        }
        var result = revisions.findByNoteIdAndIsDelFalseOrderByNoteVersionDescIdDesc(noteId, PageRequest.of(page, 20));
        var editorIds = result.stream().map(TripNoteRevisionEntity::getEditorId).distinct().toList();
        var editors = users.findAllByIdIn(editorIds).stream().collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        return PageResponse.from(result.map(revision -> {
            var editor = editors.get(revision.getEditorId());
            return new TripNoteRevisionResponse(hashids.encode(revision.getId()), hashids.encode(revision.getNoteId()),
                    hashids.encode(revision.getEditorId()), editor == null ? null : editor.getName(),
                    editor == null ? null : editor.getAvatarUrl(), revision.getContent(), revision.getImageUrl(),
                    revision.getNoteVersion(), revision.getCreatedAt(), revision.isBaseline());
        }));
    }

    private TripEntity requireTrip(Long tripId, boolean forUpdate) {
        TripEntity trip = forUpdate ? trips.findByIdForUpdate(tripId).orElse(null) : trips.findActiveTrip(tripId);
        if (trip == null || trip.isDel() || trip.getStatus() == TripStatus.DELETED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
        }
        return trip;
    }

    private TripNoteEntity requireNote(Long tripId, Long noteId) {
        return notes.findByIdAndTripIdAndIsDelFalse(noteId, tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found"));
    }

    private void validateContent(String content, boolean hasImage) {
        if ((!hasImage && (content == null || content.isBlank())) || (content != null && content.length() > 2000)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Note content must contain 1 to 2000 characters");
        }
    }

    private TripNoteResponse response(TripNoteEntity note, UserEntity author, Long actorId, boolean manager) {
        boolean own = actorId.equals(note.getAuthorId());
        return new TripNoteResponse(hashids.encode(note.getId()), hashids.encode(note.getTripId()),
                hashids.encode(note.getAuthorId()), author == null ? null : author.getName(),
                author == null ? null : author.getAvatarUrl(), note.getContent(), note.getImageUrl(), note.getVersion(),
                note.getCreatedAt(), note.getUpdatedAt(), own, own || manager);
    }
}
