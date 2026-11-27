package com.tripbudget.tripbudget_core.plantemplate.repositories;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PlanTemplateSummaryRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final String SELECT_METADATA = """
            SELECT template.id,
                   template.cover_url,
                   template.source_trip_id,
                   template.title,
                   template.destination,
                   template.description,
                   template.tags,
                   template.duration_days,
                   template.activity_count,
                   template.estimated_cost,
                   template.currency,
                   template.status,
                   template.content_version,
                   COALESCE(author.name, '') AS author_name,
                   (SELECT COUNT(*)
                    FROM trip_core.plan_template_copies receipt
                    WHERE receipt.template_id = template.id) AS usage_count
            """;

    private static final String FROM_TEMPLATES = """
            FROM trip_core.plan_templates template
            JOIN trip_core.trips source_trip ON source_trip.id = template.source_trip_id
            LEFT JOIN trip_core.users author ON author.id = template.author_id
            """;

    private static final String SEARCH_FILTERS = """
            WHERE template.is_del = FALSE
              AND source_trip.is_del = FALSE
              AND source_trip.status <> 'DELETED'
              AND ((:authorId IS NULL AND template.status = 'PUBLISHED')
                   OR template.author_id = :authorId)
              AND (:searchPattern IS NULL
                   OR LOWER(template.title) LIKE LOWER(:searchPattern) ESCAPE '!'
                   OR LOWER(template.destination) LIKE LOWER(:searchPattern) ESCAPE '!')
              AND (:durationDays IS NULL OR template.duration_days = :durationDays)
              AND (:minDurationDays IS NULL OR template.duration_days >= :minDurationDays)
              AND (:tag IS NULL OR :tag = ANY(string_to_array(template.tags, ',')))
            """;

    private static final String ORDER_BY_RECENT = "ORDER BY template.published_at DESC, template.id DESC ";
    private static final String ORDER_BY_POPULAR = "ORDER BY usage_count DESC, template.id DESC ";
    private static final String PAGINATION = "LIMIT :pageSize OFFSET :offset";

    private static final RowMapper<PlanTemplateSummary> SUMMARY_MAPPER = (row, rowNumber) ->
            new PlanTemplateSummary(
                    row.getLong("id"),
                    row.getLong("source_trip_id"),
                    row.getString("title"),
                    row.getString("destination"),
                    row.getString("description"),
                    row.getString("tags"),
                    row.getInt("duration_days"),
                    row.getInt("activity_count"),
                    row.getBigDecimal("estimated_cost"),
                    row.getString("currency"),
                    row.getString("status"),
                    row.getInt("content_version"),
                    row.getString("author_name"),
                    row.getLong("usage_count"),
                    row.getString("cover_url")
            );

    // One overview result per published itinerary. Stops are queried only after selection.
    private static final String MAP_FILTERS = """
        FROM trip_core.plan_templates template
        JOIN trip_core.trips source_trip ON source_trip.id = template.source_trip_id
        WHERE template.is_del = FALSE AND template.status = 'PUBLISHED'
          AND source_trip.is_del = FALSE AND source_trip.status <> 'DELETED'
          AND (:category = '' OR EXISTS (
            SELECT 1 FROM jsonb_array_elements(template.snapshot->'days') AS day
            CROSS JOIN LATERAL jsonb_array_elements(day->'activities') AS activity
            WHERE CASE activity->>'category'
                WHEN 'FOOD_BEVERAGE' THEN 'FOOD' WHEN 'ACCOMMODATION' THEN 'STAY'
                WHEN 'VISIT' THEN 'SIGHT' WHEN 'SIGHTSEEING' THEN 'SIGHT' ELSE 'OTHER' END = :category
          ))
          AND (:search IS NULL OR lower(template.title) LIKE :search ESCAPE '!'
               OR lower(template.destination) LIKE :search ESCAPE '!'
               OR EXISTS (SELECT 1 FROM jsonb_array_elements(template.snapshot->'days') AS day
                 CROSS JOIN LATERAL jsonb_array_elements(day->'activities') AS activity
                 WHERE lower(activity->>'title') LIKE :search ESCAPE '!'
                    OR lower(activity->>'location') LIKE :search ESCAPE '!'))
        """;

    public Page<PlanTemplateMapSource> mapTemplates(String search, String category, org.springframework.data.domain.Pageable page) {
        var parameters = new MapSqlParameterSource()
            .addValue("search", searchPattern(search.toLowerCase(java.util.Locale.ROOT)), Types.VARCHAR)
            .addValue("category", category).addValue("limit", page.getPageSize()).addValue("offset", page.getOffset());
        Long total = jdbcTemplate.queryForObject("SELECT count(*) " + MAP_FILTERS, parameters, Long.class);
        var items = jdbcTemplate.query("""
            SELECT template.id, template.title, template.destination, template.cover_url, template.duration_days,
                (SELECT count(*) FROM trip_core.plan_template_saves saved WHERE saved.template_id = template.id) AS like_count
            """ + MAP_FILTERS + " ORDER BY template.published_at DESC, template.id DESC LIMIT :limit OFFSET :offset", parameters,
            (row, index) -> new PlanTemplateMapSource(row.getLong("id"), row.getString("title"), row.getString("destination"),
                row.getString("cover_url"), row.getInt("duration_days"), row.getLong("like_count")));
        return new PageImpl<>(items, page, total == null ? 0 : total);
    }

    public Page<PlanTemplateSummary> search(PlanTemplateSearchCriteria criteria) {
        MapSqlParameterSource parameters = searchParameters(criteria);
        String countQuery = "SELECT COUNT(*) " + FROM_TEMPLATES + SEARCH_FILTERS;
        Long totalElements = jdbcTemplate.queryForObject(countQuery, parameters, Long.class);

        String orderBy = criteria.popularFirst() ? ORDER_BY_POPULAR : ORDER_BY_RECENT;
        String pageQuery = SELECT_METADATA + FROM_TEMPLATES + SEARCH_FILTERS + orderBy + PAGINATION;
        List<PlanTemplateSummary> content = jdbcTemplate.query(pageQuery, parameters, SUMMARY_MAPPER);
        return new PageImpl<>(content, criteria.pageable(), totalElements == null ? 0 : totalElements);
    }

    public Optional<PlanTemplateSummary> findById(Long templateId) {
        String query = SELECT_METADATA + FROM_TEMPLATES + "WHERE template.id = :templateId";
        MapSqlParameterSource parameters = new MapSqlParameterSource("templateId", templateId);
        return jdbcTemplate.query(query, parameters, SUMMARY_MAPPER).stream().findFirst();
    }

    private MapSqlParameterSource searchParameters(PlanTemplateSearchCriteria criteria) {
        return new MapSqlParameterSource()
                .addValue("authorId", criteria.authorId(), Types.BIGINT)
                .addValue("searchPattern", searchPattern(criteria.search()), Types.VARCHAR)
                .addValue("durationDays", criteria.durationDays(), Types.INTEGER)
                .addValue("minDurationDays", criteria.minDurationDays(), Types.INTEGER)
                .addValue("tag", blankToNull(criteria.tag()), Types.VARCHAR)
                .addValue("pageSize", criteria.pageable().getPageSize(), Types.INTEGER)
                .addValue("offset", criteria.pageable().getOffset(), Types.BIGINT);
    }

    private String searchPattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String escapedSearch = search.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escapedSearch + "%";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
