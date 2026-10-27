package com.tripbudget.tripbudget_core.plantemplate.services;

import com.tripbudget.tripbudget_core.plantemplate.access.PlanTemplateAccessService;
import com.tripbudget.tripbudget_core.plantemplate.dtos.response.PlanTemplateCardResponse;
import com.tripbudget.tripbudget_core.plantemplate.dtos.response.PlanTemplateDetailResponse;
import com.tripbudget.tripbudget_core.plantemplate.dtos.response.PlanTemplateListResponse;
import com.tripbudget.tripbudget_core.plantemplate.entities.PlanTemplateEntity;
import com.tripbudget.tripbudget_core.plantemplate.mappers.PlanTemplateResponseMapper;
import com.tripbudget.tripbudget_core.plantemplate.repositories.PlanTemplateRepository;
import com.tripbudget.tripbudget_core.plantemplate.repositories.PlanTemplateSearchCriteria;
import com.tripbudget.tripbudget_core.plantemplate.repositories.PlanTemplateSummary;
import com.tripbudget.tripbudget_core.plantemplate.repositories.PlanTemplateSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanTemplateQueryService {
    private final PlanTemplateRepository templateRepository;
    private final PlanTemplateSummaryRepository summaryRepository;
    private final PlanTemplateResponseMapper responseMapper;
    private final PlanTemplateAccessService accessService;

    public PlanTemplateListResponse listPublicTemplates(
            String search, Integer duration, String tag, String sort, int page, int size
    ) {
        validateFilters(search, page, size);
        PlanTemplateSearchCriteria criteria = new PlanTemplateSearchCriteria(
                null, search, duration, tag, "popular".equals(sort), PageRequest.of(page, size)
        );
        Page<PlanTemplateSummary> summaries = summaryRepository.search(criteria);
        List<PlanTemplateCardResponse> cards = summaries.getContent().stream()
                .map(responseMapper::toPublicCard).toList();
        return toListResponse(summaries, cards);
    }

    public PlanTemplateListResponse listMyTemplates(Long currentUserId, int page, int size) {
        validateFilters("", page, size);
        PlanTemplateSearchCriteria criteria = new PlanTemplateSearchCriteria(
                currentUserId, "", null, null, false, PageRequest.of(page, size)
        );
        Page<PlanTemplateSummary> summaries = summaryRepository.search(criteria);
        List<PlanTemplateCardResponse> cards = summaries.getContent().stream()
                .map(responseMapper::toOwnedCard).toList();
        return toListResponse(summaries, cards);
    }

    public PlanTemplateDetailResponse getPublicTemplate(Long templateId) {
        PlanTemplateEntity template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TEMPLATE_NOT_FOUND"));
        accessService.requirePublished(template);
        return toDetailResponse(template);
    }

    public PlanTemplateDetailResponse toDetailResponse(PlanTemplateEntity template) {
        PlanTemplateSummary summary = summaryRepository.findById(template.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TEMPLATE_NOT_FOUND"));
        return new PlanTemplateDetailResponse(responseMapper.toPublicCard(summary), template.getSnapshot());
    }

    private void validateFilters(String search, int page, int size) {
        if (page < 0 || page > 10_000 || size < 1 || size > 24 || search == null || search.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_TEMPLATE_FILTER");
        }
    }

    private PlanTemplateListResponse toListResponse(
            Page<PlanTemplateSummary> summaries, List<PlanTemplateCardResponse> cards
    ) {
        return new PlanTemplateListResponse(
                cards, summaries.getNumber(), summaries.getSize(),
                summaries.getTotalElements(), summaries.getTotalPages()
        );
    }
}
