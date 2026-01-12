package com.nala.armoire.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.nala.armoire.model.document.DesignDocument;
import com.nala.armoire.model.dto.response.DesignCategoryDTO;
import com.nala.armoire.model.dto.response.DesignListDTO;
import com.nala.armoire.model.dto.response.PagedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Service for Elasticsearch-based design search operations
 * Provides fast full-text search, filtering, and sorting
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DesignSearchService {

    private final ElasticsearchClient elasticsearchClient;

    /**
     * Search and filter designs using Elasticsearch
     * 
     * @param categorySlug   Filter by design category slug
     * @param searchQuery    Full-text search query (searches name, description, tags)
     * @param isPremium      Filter by premium status
     * @param pageable       Pagination and sorting
     * @return Paged response of designs
     */
    public PagedResponse<DesignListDTO> searchDesigns(
            String categorySlug,
            String searchQuery,
            Boolean isPremium,
            Pageable pageable) {
        try {
            log.info("Elasticsearch design search - categorySlug: {}, query: {}, isPremium: {}, page: {}", 
                    categorySlug, searchQuery, isPremium, pageable.getPageNumber());

            // Build boolean query
            BoolQuery.Builder boolQuery = new BoolQuery.Builder();

            // Must be active
            boolQuery.must(m -> m.term(t -> t.field("isActive").value(true)));

            // Filter by category slug
            if (categorySlug != null && !categorySlug.trim().isEmpty()) {
                log.debug("Adding category filter - Field: designCategorySlug, Value: {}", categorySlug);
                boolQuery.filter(f -> f.term(t -> t
                        .field("designCategorySlug")
                        .value(categorySlug)));
            }

            // Filter by premium status
            if (isPremium != null) {
                boolQuery.filter(f -> f.term(t -> t
                        .field("isPremium")
                        .value(isPremium)));
            }

            // Full-text search across multiple fields
            if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                boolQuery.must(m -> m.bool(b -> b
                        // Use multi_match with phrase_prefix for partial word matching
                        .should(s -> s.multiMatch(mm -> mm
                                .query(searchQuery)
                                .fields("name^3", "description^2", "tagsText", "designCategoryName")
                                .type(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.PhrasePrefix)))
                        // Also use wildcard for substring matching (e.g., "mod" matches "modern")
                        .should(s -> s.wildcard(w -> w
                                .field("name")
                                .value("*" + searchQuery.toLowerCase() + "*")
                                .boost(2.0f)))
                        // Fallback to fuzzy matching for typo tolerance
                        .should(s -> s.multiMatch(mm -> mm
                                .query(searchQuery)
                                .fields("name^3", "description^2", "tagsText", "designCategoryName")
                                .fuzziness("AUTO")))
                        .minimumShouldMatch("1")));
            }

            // Parse sorting from Pageable
            List<co.elastic.clients.elasticsearch._types.SortOptions> sortOptions = parseSortFromPageable(pageable);

            // Build and execute search request
            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index("designs")
                    .query(q -> q.bool(boolQuery.build()))
                    .from((int) pageable.getOffset())
                    .size(pageable.getPageSize())
                    .sort(sortOptions));

            log.debug("Elasticsearch query: {}", searchRequest.toString());

            SearchResponse<DesignDocument> response = elasticsearchClient.search(
                    searchRequest,
                    DesignDocument.class);

            // Map results to DTOs
            List<DesignListDTO> designs = response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .peek(doc -> log.debug("ES Document - ID: {}, Category Slug: {}, Design: {}", 
                            doc.getId(), doc.getDesignCategorySlug(), doc.getName()))
                    .map(this::mapToDesignListDTO)
                    .collect(Collectors.toList());

            @SuppressWarnings("null")
            long totalHits = (response.hits() != null && response.hits().total() != null)
                    ? response.hits().total().value()
                    : 0;

            log.info("Elasticsearch design search returned {} results out of {} total", 
                    designs.size(), totalHits);

            // Build paged response
            return PagedResponse.<DesignListDTO>builder()
                    .content(designs)
                    .page(pageable.getPageNumber())
                    .size(pageable.getPageSize())
                    .totalElements(totalHits)
                    .totalPages((int) Math.ceil((double) totalHits / pageable.getPageSize()))
                    .first(pageable.getPageNumber() == 0)
                    .last((long) (pageable.getPageNumber() + 1) * pageable.getPageSize() >= totalHits)
                    .hasNext((long) (pageable.getPageNumber() + 1) * pageable.getPageSize() < totalHits)
                    .hasPrevious(pageable.getPageNumber() > 0)
                    .build();

        } catch (Exception e) {
            log.error("Error searching designs in Elasticsearch", e);
            throw new RuntimeException("Failed to search designs", e);
        }
    }

    /**
     * Map DesignDocument to DesignListDTO
     */
    private DesignListDTO mapToDesignListDTO(DesignDocument doc) {
        return DesignListDTO.builder()
                .id(java.util.UUID.fromString(doc.getId()))
                .name(doc.getName())
                .slug(doc.getSlug())
                .description(doc.getDescription())
                .imageUrl(doc.getDesignImageUrl())
                .thumbnailUrl(doc.getThumbnailUrl())
                .category(DesignCategoryDTO.builder()
                        .id(java.util.UUID.fromString(doc.getDesignCategoryId()))
                        .name(doc.getDesignCategoryName())
                        .slug(doc.getDesignCategorySlug())
                        .build())
                .tags(doc.getTags() != null ? doc.getTags() : new ArrayList<>())
                .isActive(doc.getIsActive())
                .isPremium(doc.getIsPremium())
                .downloadCount(doc.getDownloadCount())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }

    /**
     * Parse sorting from Spring Data Pageable
     */
    private List<co.elastic.clients.elasticsearch._types.SortOptions> parseSortFromPageable(Pageable pageable) {
        List<co.elastic.clients.elasticsearch._types.SortOptions> sortOptions = new ArrayList<>();

        if (pageable.getSort().isSorted()) {
            for (Sort.Order order : pageable.getSort()) {
                String fieldName = mapSortField(order.getProperty());
                SortOrder sortOrder = order.isAscending() ? SortOrder.Asc : SortOrder.Desc;

                sortOptions.add(co.elastic.clients.elasticsearch._types.SortOptions.of(so -> so
                        .field(f -> f
                                .field(fieldName)
                                .order(sortOrder))));
            }
        } else {
            // Default sort: most recent first
            sortOptions.add(co.elastic.clients.elasticsearch._types.SortOptions.of(so -> so
                    .field(f -> f
                            .field("createdAt")
                            .order(SortOrder.Desc))));
        }

        return sortOptions;
    }

    /**
     * Map DTO field names to Elasticsearch field names
     */
    private String mapSortField(String dtoField) {
        return switch (dtoField) {
            case "name" -> "name.keyword";
            case "createdAt" -> "createdAt";
            case "downloadCount" -> "downloadCount";
            case "updatedAt" -> "updatedAt";
            default -> "createdAt";
        };
    }
}
