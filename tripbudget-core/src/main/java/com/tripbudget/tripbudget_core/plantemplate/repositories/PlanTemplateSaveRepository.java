package com.tripbudget.tripbudget_core.plantemplate.repositories;

import com.tripbudget.tripbudget_core.plantemplate.entities.PlanTemplateSaveEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlanTemplateSaveRepository extends JpaRepository<PlanTemplateSaveEntity, Long> {
    boolean existsByUserIdAndTemplateId(Long userId, Long templateId);

    @Modifying
    @Query("""
            DELETE FROM PlanTemplateSaveEntity saved
            WHERE saved.userId = :userId AND saved.templateId = :templateId
            """)
    int deleteByUserIdAndTemplateId(@Param("userId") Long userId, @Param("templateId") Long templateId);

    @Query("""
            SELECT saved.templateId
            FROM PlanTemplateSaveEntity saved
            JOIN PlanTemplateEntity template ON template.id = saved.templateId
            JOIN TripEntity sourceTrip ON sourceTrip.id = template.sourceTripId
            WHERE saved.userId = :userId AND saved.templateId IN :templateIds
              AND template.status = PlanTemplateStatus.PUBLISHED AND template.isDel = FALSE
              AND sourceTrip.isDel = FALSE AND sourceTrip.status <> TripStatus.DELETED
            """)
    List<Long> findSavedIds(@Param("userId") Long userId, @Param("templateIds") List<Long> templateIds);

    @Query(value = """
            SELECT new com.tripbudget.tripbudget_core.plantemplate.repositories.PlanTemplateSummary(
                template.id, template.sourceTripId, template.title, template.destination,
                template.description, template.tags, template.durationDays, template.activityCount,
                template.estimatedCost, template.currency, cast(template.status as string),
                template.contentVersion, coalesce(author.name, ''),
                (SELECT COUNT(receipt) FROM PlanTemplateCopyEntity receipt WHERE receipt.templateId = template.id)
            )
            FROM PlanTemplateSaveEntity saved
            JOIN PlanTemplateEntity template ON template.id = saved.templateId
            JOIN TripEntity sourceTrip ON sourceTrip.id = template.sourceTripId
            LEFT JOIN UserEntity author ON author.id = template.authorId
            WHERE saved.userId = :userId
              AND template.status = PlanTemplateStatus.PUBLISHED AND template.isDel = FALSE
              AND sourceTrip.isDel = FALSE AND sourceTrip.status <> TripStatus.DELETED
            ORDER BY saved.createdAt DESC, saved.id DESC
            """, countQuery = """
            SELECT COUNT(saved)
            FROM PlanTemplateSaveEntity saved
            JOIN PlanTemplateEntity template ON template.id = saved.templateId
            JOIN TripEntity sourceTrip ON sourceTrip.id = template.sourceTripId
            WHERE saved.userId = :userId
              AND template.status = PlanTemplateStatus.PUBLISHED AND template.isDel = FALSE
              AND sourceTrip.isDel = FALSE AND sourceTrip.status <> TripStatus.DELETED
            """)
    Page<PlanTemplateSummary> findVisibleSavedTemplates(@Param("userId") Long userId, Pageable pageable);
}
