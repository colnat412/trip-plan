package com.tripbudget.tripbudget_core.admin.repositories;

import com.tripbudget.tripbudget_core.admin.entities.AdminAuditEntity;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditRepository extends JpaRepository<AdminAuditEntity, Long> {
    Page<AdminAuditEntity> findByIsDelFalseOrderByCreatedAtDescIdDesc(Pageable pageable);
}
