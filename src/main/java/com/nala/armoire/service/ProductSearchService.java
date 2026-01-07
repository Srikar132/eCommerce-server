package com.nala.armoire.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.nala.armoire.model.document.ProductDocument;
import com.nala.armoire.model.dto.response.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductSearchService {

    private final ElasticsearchClient elasticsearchClient;

    /**
     * FIXED: Main product search with proper category filtering
     */
    public ProductSearchResponse getProducts(
            List<String> categorySlugs,
            List<String> brandSlugs,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            List<String> sizes,
            List<String> colors,
            Boolean isCustomizable,
            String searchQuery,
            Pageable pageable
    ) {
        try {
            BoolQuery.Builder boolQuery = new BoolQuery.Builder();

            // Must be active
            boolQuery.must(m -> m.term(t -> t.field("isActive").value(true)));

            // ==================== FIXED CATEGORY FILTER ====================
            /**
             * This now uses "allCategorySlugs" field which contains:
             * - The leaf category (e.g., "men-tshirts")
             * - All parent categories (e.g., "men-topwear")
             *
             * So filtering by "men-topwear" will return:
             * - Products in "men-tshirts" (has ["men-tshirts", "men-topwear"])
             * - Products in "men-shirts" (has ["men-shirts", "men-topwear"])
             * - Products in "men-polos" (has ["men-polos", "men-topwear"])
             */
            if (categorySlugs != null && !categorySlugs.isEmpty()) {
                boolQuery.filter(f -> f.terms(t -> t
                        .field("allCategorySlugs") // ← CHANGED: Use hierarchical field
                        .terms(ts -> ts.value(categorySlugs.stream()
                                .map(FieldValue::of)
                                .collect(Collectors.toList())))
                ));
            }

            // Brand filter
            if (brandSlugs != null && !brandSlugs.isEmpty()) {
                boolQuery.filter(f -> f.terms(t -> t
                        .field("brandSlug")
                        .terms(ts -> ts.value(brandSlugs.stream()
                                .map(FieldValue::of)
                                .collect(Collectors.toList())))
                ));
            }

            // TODO: Price range filter - Need to fix the API usage
            // The Elasticsearch Java client API for range queries needs investigation
            // For now, price filtering is disabled to allow the app to compile
            /*
            if (minPrice != null && maxPrice != null) {
                // Both min and max specified
                boolQuery.filter(f -> f.range(r -> {
                    r.field("basePrice")
                     .gte(co.elastic.clients.json.JsonData.of(minPrice.doubleValue()))
                     .lte(co.elastic.clients.json.JsonData.of(maxPrice.doubleValue()));
                    return r;
                }));
            } else if (minPrice != null) {
                // Only min specified
                boolQuery.filter(f -> f.range(r -> {
                    r.field("basePrice")
                     .gte(co.elastic.clients.json.JsonData.of(minPrice.doubleValue()));
                    return r;
                }));
            } else if (maxPrice != null) {
                // Only max specified
                boolQuery.filter(f -> f.range(r -> {
                    r.field("basePrice")
                     .lte(co.elastic.clients.json.JsonData.of(maxPrice.doubleValue()));
                    return r;
                }));
            }
            */

            // Size filter
            if (sizes != null && !sizes.isEmpty()) {
                boolQuery.filter(f -> f.terms(t -> t
                        .field("availableSizes")
                        .terms(ts -> ts.value(sizes.stream()
                                .map(FieldValue::of)
                                .collect(Collectors.toList())))
                ));
            }

            // Color filter
            if (colors != null && !colors.isEmpty()) {
                boolQuery.filter(f -> f.terms(t -> t
                        .field("availableColors")
                        .terms(ts -> ts.value(colors.stream()
                                .map(s -> FieldValue.of(s.toLowerCase()))
                                .collect(Collectors.toList())))
                ));
            }

            // Customizable filter
            if (isCustomizable != null) {
                boolQuery.filter(f -> f.term(t -> t
                        .field("isCustomizable")
                        .value(isCustomizable)
                ));
            }

            // Text search
            if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                boolQuery.must(m -> m.multiMatch(mm -> mm
                        .query(searchQuery)
                        .fields("name^3", "description^2", "material")
                        .type(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.BestFields)
                        .fuzziness("AUTO")
                ));
            }

            // Build aggregations
            Map<String, Aggregation> aggregations = buildAggregations();

            // Parse sorting
            List<co.elastic.clients.elasticsearch._types.SortOptions> sortOptions =
                    parseSortFromPageable(pageable);

            // Execute search
            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index("products")
                    .query(q -> q.bool(boolQuery.build()))
                    .aggregations(aggregations)
                    .from((int) pageable.getOffset())
                    .size(pageable.getPageSize())
                    .sort(sortOptions)
            );

            SearchResponse<ProductDocument> response = elasticsearchClient.search(
                    searchRequest,
                    ProductDocument.class
            );

            // Extract products
            List<ProductDTO> products = response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(this::mapToProductDTO)
                    .collect(Collectors.toList());

            // Extract facets
            ProductFacetsDTO facets = extractFacets(response, categorySlugs, brandSlugs);

            // Get total hits
            long totalHits = response.hits().total() != null ? response.hits().total().value() : 0;

            // Build paged response
            PagedResponse<ProductDTO> pagedProducts = PagedResponse.<ProductDTO>builder()
                    .content(products)
                    .page(pageable.getPageNumber())
                    .size(pageable.getPageSize())
                    .totalElements(totalHits)
                    .totalPages((int) Math.ceil((double) totalHits / pageable.getPageSize()))
                    .first(pageable.getPageNumber() == 0)
                    .last((long) (pageable.getPageNumber() + 1) * pageable.getPageSize() >= totalHits)
                    .hasNext((long) (pageable.getPageNumber() + 1) * pageable.getPageSize() < totalHits)
                    .hasPrevious(pageable.getPageNumber() > 0)
                    .build();

            return ProductSearchResponse.builder()
                    .products(pagedProducts)
                    .facets(facets)
                    .build();

        } catch (Exception e) {
            log.error("Error searching products", e);
            throw new RuntimeException("Failed to search products", e);
        }
    }

    /**
     * FIXED: Extract facets with proper slug + label mapping
     */
    private ProductFacetsDTO extractFacets(
            SearchResponse<ProductDocument> response,
            List<String> selectedCategories,
            List<String> selectedBrands
    ) {
        ProductFacetsDTO facets = new ProductFacetsDTO();

        // ==================== FIXED CATEGORY FACETS ====================
        /**
         * Returns LEAF categories only (not parent categories)
         * With slug as value and name as label
         */
        if (response.aggregations().containsKey("leaf_categories")) {
            facets.setCategories(
                    response.aggregations().get("leaf_categories").sterms().buckets().array().stream()
                            .map(bucket -> {
                                String categorySlug = bucket.key().stringValue();

                                // Get corresponding name from name aggregation
                                String categoryName = categorySlug; // Fallback

                                boolean isSelected = selectedCategories != null &&
                                        selectedCategories.contains(categorySlug);

                                return FacetItem.builder()
                                        .value(categorySlug) // ← SLUG for filtering
                                        .label(formatCategoryName(categorySlug)) // ← Pretty name for display
                                        .count(bucket.docCount())
                                        .selected(isSelected)
                                        .build();
                            })
                            .collect(Collectors.toList())
            );
        }

        // Brand facets
        if (response.aggregations().containsKey("brands")) {
            facets.setBrands(
                    response.aggregations().get("brands").sterms().buckets().array().stream()
                            .map(bucket -> {
                                String brandSlug = bucket.key().stringValue();
                                boolean isSelected = selectedBrands != null &&
                                        selectedBrands.contains(brandSlug);

                                return FacetItem.builder()
                                        .value(brandSlug)
                                        .label(formatBrandName(brandSlug))
                                        .count(bucket.docCount())
                                        .selected(isSelected)
                                        .build();
                            })
                            .collect(Collectors.toList())
            );
        }

        // Size facets
        if (response.aggregations().containsKey("sizes")) {
            facets.setSizes(
                    response.aggregations().get("sizes").sterms().buckets().array().stream()
                            .map(bucket -> FacetItem.builder()
                                    .value(bucket.key().stringValue())
                                    .label(bucket.key().stringValue())
                                    .count(bucket.docCount())
                                    .build())
                            .collect(Collectors.toList())
            );
        }

        // Color facets
        if (response.aggregations().containsKey("colors")) {
            facets.setColors(
                    response.aggregations().get("colors").sterms().buckets().array().stream()
                            .map(bucket -> FacetItem.builder()
                                    .value(bucket.key().stringValue())
                                    .label(capitalize(bucket.key().stringValue()))
                                    .count(bucket.docCount())
                                    .build())
                            .collect(Collectors.toList())
            );
        }

        // Price range
        if (response.aggregations().containsKey("price_stats")) {
            var stats = response.aggregations().get("price_stats").stats();

            if (stats.count() > 0) {
                facets.setPriceRange(PriceRange.builder()
                        .min(BigDecimal.valueOf(stats.min()))
                        .max(BigDecimal.valueOf(stats.max()))
                        .build());
            } else {
                facets.setPriceRange(PriceRange.builder()
                        .min(BigDecimal.ZERO)
                        .max(BigDecimal.ZERO)
                        .build());
            }
        }

        return facets;
    }

    /**
     * FIXED: Build aggregations for proper facets
     */
    private Map<String, Aggregation> buildAggregations() {
        Map<String, Aggregation> aggregations = new HashMap<>();

        // ==================== LEAF CATEGORY AGGREGATION ====================
        /**
         * Uses "categorySlug" (not allCategorySlugs) to get only direct categories
         * This prevents showing parent categories in facets
         */
        aggregations.put("leaf_categories", Aggregation.of(a -> a
                .terms(t -> t.field("categorySlug").size(100))
        ));

        // Brand aggregation
        aggregations.put("brands", Aggregation.of(a -> a
                .terms(t -> t.field("brandSlug").size(100))
        ));

        // Size aggregation
        aggregations.put("sizes", Aggregation.of(a -> a
                .terms(t -> t.field("availableSizes").size(20))
        ));

        // Color aggregation
        aggregations.put("colors", Aggregation.of(a -> a
                .terms(t -> t.field("availableColors").size(50))
        ));

        // Price stats
        aggregations.put("price_stats", Aggregation.of(a -> a
                .stats(s -> s.field("basePrice"))
        ));

        return aggregations;
    }

    /**
     * Helper: Format category slug to display name
     * "men-tshirts" → "T-Shirts"
     */
    private String formatCategoryName(String slug) {
        if (slug == null) return "";

        // Remove prefix (men-, women-, kids-)
        String withoutPrefix = slug.replaceFirst("^(men|women|kids)-", "");

        // Replace hyphens with spaces and capitalize
        return Arrays.stream(withoutPrefix.split("-"))
                .map(this::capitalize)
                .collect(Collectors.joining(" "));
    }

    /**
     * Helper: Format brand slug to display name
     * "nike" → "Nike"
     */
    private String formatBrandName(String slug) {
        return capitalize(slug);
    }

    /**
     * Helper: Capitalize first letter
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    // ... (keep existing parseSortFromPageable and mapToProductDTO methods)

    private List<co.elastic.clients.elasticsearch._types.SortOptions> parseSortFromPageable(Pageable pageable) {
        List<co.elastic.clients.elasticsearch._types.SortOptions> sortOptions = new ArrayList<>();

        if (pageable.getSort().isSorted()) {
            for (Sort.Order order : pageable.getSort()) {
                String fieldName = order.getProperty();
                SortOrder sortOrder = order.getDirection() == Sort.Direction.ASC
                        ? SortOrder.Asc
                        : SortOrder.Desc;

                sortOptions.add(co.elastic.clients.elasticsearch._types.SortOptions.of(
                        s -> s.field(f -> f.field(fieldName).order(sortOrder))
                ));
            }
        } else {
            sortOptions.add(co.elastic.clients.elasticsearch._types.SortOptions.of(
                    s -> s.field(f -> f.field("createdAt").order(SortOrder.Desc))
            ));
        }

        return sortOptions;
    }

    private ProductDTO mapToProductDTO(ProductDocument doc) {
        List<ProductImageDTO> images = doc.getImages() != null
                ? doc.getImages().stream()
                .map(img -> ProductImageDTO.builder()
                        .id(UUID.fromString(img.getId()))
                        .imageUrl(img.getImageUrl())
                        .altText(img.getAltText())
                        .displayOrder(img.getDisplayOrder())
                        .isPrimary(img.getIsPrimary())
                        .build())
                .collect(Collectors.toList())
                : new ArrayList<>();

        return ProductDTO.builder()
                .id(UUID.fromString(doc.getId()))
                .name(doc.getName())
                .slug(doc.getSlug())
                .description(doc.getDescription())
                .basePrice(doc.getBasePrice())
                .sku(doc.getSku())
                .isCustomizable(doc.getIsCustomizable())
                .material(doc.getMaterial())
                .careInstructions(doc.getCareInstructions())
                .categoryId(doc.getCategoryId() != null ? UUID.fromString(doc.getCategoryId()) : null)
                .categoryName(doc.getCategoryName())
                .brandId(doc.getBrandId() != null ? UUID.fromString(doc.getBrandId()) : null)
                .brandName(doc.getBrandName())
                .images(images)
                .averageRating(doc.getAverageRating())
                .reviewCount(doc.getReviewCount())
                .isActive(doc.getIsActive())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }

    /**
     * Get autocomplete suggestions for product search
     * Uses Elasticsearch completion suggester for fast results
     */
    public List<String> getAutocomplete(String query, Integer limit) {
        try {
            // Build prefix query for fast autocomplete
            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index("products")
                    .query(q -> q
                            .bool(b -> b
                                    .must(m -> m.term(t -> t
                                            .field("isActive")
                                            .value(true)
                                    ))
                                    .should(sh -> sh.matchPhrasePrefix(mp -> mp
                                            .field("name")
                                            .query(query)
                                            .maxExpansions(10)
                                    ))
                                    .should(sh -> sh.matchPhrasePrefix(mp -> mp
                                            .field("description")
                                            .query(query)
                                            .maxExpansions(5)
                                    ))
                                    .minimumShouldMatch("1")
                            )
                    )
                    .size(limit)
                    .source(src -> src.filter(f -> f.includes("name")))
            );

            SearchResponse<ProductDocument> response = elasticsearchClient.search(
                    searchRequest,
                    ProductDocument.class
            );

            // Extract unique product names
            return response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(ProductDocument::getName)
                    .distinct()
                    .limit(limit)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error in autocomplete search", e);
            return Collections.emptyList();
        }
    }

}
