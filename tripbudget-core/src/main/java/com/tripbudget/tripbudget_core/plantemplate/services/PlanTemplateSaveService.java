package com.tripbudget.tripbudget_core.plantemplate.services;

import com.tripbudget.tripbudget_core.plantemplate.access.PlanTemplateAccessService;
import com.tripbudget.tripbudget_core.plantemplate.entities.PlanTemplateSaveEntity;
import com.tripbudget.tripbudget_core.plantemplate.repositories.PlanTemplateRepository;
import com.tripbudget.tripbudget_core.plantemplate.repositories.PlanTemplateSaveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanTemplateSaveService {
    private final PlanTemplateRepository templates;
    private final PlanTemplateSaveRepository saves;
    private final PlanTemplateAccessService access;

    @Transactional
    public void save(Long currentUserId, Long templateId) {
        var template = templates.findByIdForUpdate(templateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TEMPLATE_NOT_FOUND"));
        if (template.isDel()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "TEMPLATE_NOT_FOUND");
        }
        access.requirePublished(template);
        if (!saves.existsByUserIdAndTemplateId(currentUserId, templateId)) {
            saves.save(PlanTemplateSaveEntity.create(currentUserId, templateId));
        }
    }

    @Transactional
    public void unsave(Long currentUserId, Long templateId) {
        templates.findByIdForUpdate(templateId);
        saves.deleteByUserIdAndTemplateId(currentUserId, templateId);
    }

    public List<Long> savedIds(Long currentUserId, List<Long> templateIds) {
        if (templateIds.size() > 24) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TOO_MANY_TEMPLATE_IDS");
        }
        if (templateIds.isEmpty()) return List.of();
        return saves.findSavedIds(currentUserId, templateIds);
    }
}
