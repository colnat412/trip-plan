package com.tripbudget.tripbudget_core.plantemplate.services;

import com.tripbudget.tripbudget_core.common.services.HashidsService;
import com.tripbudget.tripbudget_core.maps.services.MapsService;
import com.tripbudget.tripbudget_core.maps.web.MapsException;
import com.tripbudget.tripbudget_core.plantemplate.repositories.PlanTemplateSummaryRepository;
import com.tripbudget.tripbudget_core.plantemplate.dtos.response.PlanTemplateMapResponse;
import com.tripbudget.tripbudget_core.plantemplate.dtos.response.PlanTemplateItineraryMapResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static com.tripbudget.tripbudget_core.plantemplate.dtos.response.PlanTemplateMapResponse.*;

@Service
@RequiredArgsConstructor
public class PlanTemplateMapService {
    private final PlanTemplateSummaryRepository summaries;
    private final MapsService maps;
    private final HashidsService ids;
    private final PlanTemplateQueryService templates;
    private static final int SIZE = 8;

    public PlanTemplateMapResponse list(String search, String category, Double west, Double south,
                                        Double east, Double north, int page) {
        if (search == null || search.length() > 200 || page < 0 || page > 5000
            || !Set.of("", "SIGHT", "FOOD", "STAY", "OTHER").contains(category)) invalid();
        boolean area = west != null || south != null || east != null || north != null;
        if (area && (!valid(west, 180) || !valid(east, 180) || !valid(south, 90) || !valid(north, 90) || south > north)) invalid();
        var candidates = summaries.mapTemplates(search, category, PageRequest.of(page, SIZE));
        var items = new ArrayList<Itinerary>();
        boolean providerFailed = false;
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        for (var source : candidates.getContent()) {
            Double latitude = null, longitude = null;
            if (System.nanoTime() > deadline) providerFailed = true;
            if (!providerFailed) {
                try {
                    var location = maps.publishedTemplateLocation(source.destination());
                    if (location != null && location.coordinates() != null
                        && valid(location.coordinates().latitude(), 85) && valid(location.coordinates().longitude(), 180)) {
                        latitude = location.coordinates().latitude(); longitude = location.coordinates().longitude();
                    }
                } catch (MapsException failure) { providerFailed = true; }
            }
            if (area && (latitude == null || latitude < south || latitude > north
                || (west <= east ? longitude < west || longitude > east : longitude < west && longitude > east))) continue;
            items.add(new Itinerary(ids.encode(source.id()), source.title(), source.destination(), source.coverUrl(),
                source.durationDays(), source.likeCount(), latitude, longitude));
        }
        return new PlanTemplateMapResponse(items, new Pagination(page, SIZE, candidates.getTotalElements(), candidates.getTotalPages(), candidates.hasNext()));
    }
    public PlanTemplateItineraryMapResponse itinerary(Long templateId, int dayNumber, int page) {
        return itinerary(templateId, dayNumber, page, null);
    }

    public PlanTemplateItineraryMapResponse itinerary(Long templateId, int dayNumber, int page, Integer expectedVersion) {
        if (dayNumber < 1 || page < 0 || page > 5000) invalid();
        // Recheck published visibility on every page, including cached coordinate lookups.
        var detail = templates.getPublicTemplate(templateId);
        if (expectedVersion != null && detail.template().contentVersion() != expectedVersion)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TEMPLATE_VERSION_CHANGED");
        var day = detail.snapshot().days().stream().filter(item -> item.dayNumber() == dayNumber).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TEMPLATE_DAY_NOT_FOUND"));
        var activities = day.activities();
        int start = Math.min(page * SIZE, activities.size());
        int end = Math.min(start + SIZE, activities.size());
        var stops = new ArrayList<PlanTemplateItineraryMapResponse.Stop>();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        boolean failed = false;
        for (int index = start; index < end; index++) {
            var activity = activities.get(index);
            Double latitude = null, longitude = null;
            String address = activity.location() == null ? "" : activity.location().trim();
            String destination = detail.template().destination();
            String lookup = !address.contains(",") && !address.toLowerCase(Locale.ROOT).contains(destination.toLowerCase(Locale.ROOT))
                ? address + ", " + destination : address;
            if (System.nanoTime() > deadline) failed = true;
            if (!address.isBlank() && !failed) {
                try {
                    var location = maps.publishedTemplateLocation(lookup);
                    if (location != null && location.coordinates() != null
                        && valid(location.coordinates().latitude(), 85) && valid(location.coordinates().longitude(), 180)) {
                        latitude = location.coordinates().latitude(); longitude = location.coordinates().longitude();
                    }
                } catch (MapsException failure) { failed = true; }
            }
            stops.add(new PlanTemplateItineraryMapResponse.Stop(
                dayNumber + ":" + index, activity.title(), index + 1, latitude, longitude));
        }
        return new PlanTemplateItineraryMapResponse(stops,
            new Pagination(page, SIZE, activities.size(), (int)Math.ceil(activities.size() / (double)SIZE), end < activities.size()));
    }

    private static boolean valid(Double value, double max) { return value != null && Double.isFinite(value) && Math.abs(value) <= max; }
    private static void invalid() { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_TEMPLATE_MAP_FILTER"); }
}
