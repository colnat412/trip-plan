package com.tripbudget.tripbudget_core.tripnote.repositories;

import com.tripbudget.tripbudget_core.tripnote.entities.TripNoteRevisionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripNoteRevisionRepository extends JpaRepository<TripNoteRevisionEntity, Long> {
    Page<TripNoteRevisionEntity> findByNoteIdAndIsDelFalseOrderByNoteVersionDescIdDesc(Long noteId, Pageable pageable);
}
