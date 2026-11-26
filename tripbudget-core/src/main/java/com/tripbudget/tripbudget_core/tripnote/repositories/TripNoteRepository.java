package com.tripbudget.tripbudget_core.tripnote.repositories;

import com.tripbudget.tripbudget_core.tripnote.entities.TripNoteEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;

public interface TripNoteRepository extends JpaRepository<TripNoteEntity, Long> {
    Page<TripNoteEntity> findByTripIdAndIsDelFalseOrderByCreatedAtDescIdDesc(Long tripId, Pageable pageable);

    boolean existsByIdAndTripIdAndIsDelFalse(Long id, Long tripId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TripNoteEntity> findByIdAndTripIdAndIsDelFalse(Long id, Long tripId);
}
