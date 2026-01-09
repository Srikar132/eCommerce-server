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

            boolQuery.must(m -> m.term(t -> t.field("isActive").value(true)));

            if (categorySlugs != null && !categorySlugs.isEmpty()) {
                boolQuery.filter(f -> f.terms(t -> t
                        .field("allCategorySlugs")
                        .terms(ts -> ts.value(categorySlugs.stream()
                                .map(FieldValue::of)
                                .collect(Collectors.toList())))
                ));
            }

            if (brandSlugs != null && !brandSlugs.isEmpty()) {
                boolQuery.filter(f -> f.terms(t -> t
                        .field("brandSlug")
                        .terms(ts -> ts.value(brandSlugs.stream()
                                .map(FieldValue::of)
                                .collect(Collectors.toList())))
                ));
            }

            if (sizes != null && !sizes.isEmpty()) {
                boolQuery.filter(f -> f.terms(t -> t
                        .field("availableSizes")
                        .terms(ts -> ts.value(sizes.stream()
                                .map(FieldValue::of)
                                .collect(Collectors.toList())))
                ));
            }

            if (colors != null && !colors.isEmpty()) {
                boolQuery.filter(f -> f.terms(t -> t
                        .field("availableColors")
                        .terms(ts -> ts.value(colors.stream()
                                .map(s -> FieldValue.of(s.toLowerCase()))
                                .collect(Collectors.toList())))
                ));
            }

            if (isCustomizable != null) {
                boolQuery.filter(f -> f.term(t -> t
                        .field("isCustomizable")
                        .value(isCustomizable)
                ));
            }

            if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                boolQuery.must(m -> m.multiMatch(mm -> mm
                        .query(searchQuery)
                        .fields("name^3", "description^2", "material")
                        .type(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.BestFields)
                        .fuzziness("AUTO")
                ));
            }

            Map<String, Aggregation> aggregations = buildAggregations();
            List<co.elastic.clients.elasticsearch._types.SortOptions> sortOptions =
                    parseSortFromPageable(pageable);

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

            List<ProductDTO> products = response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(this::mapToProductDTO)
                    .collect(Collectors.toList());

            ProductFacetsDTO facets = extractFacets(response, categorySlugs, brandSlugs);
            long totalHits = response.hits().total() != null ? response.hits().total().value() : 0;

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

    private ProductFacetsDTO extractFacets(
            SearchResponse<ProductDocument> response,
            List<String> selectedCategories,
            List<String> selectedBrands
    ) {
        ProductFacetsDTO facets = new ProductFacetsDTO();

        if (response.aggregations().containsKey("leaf_categories")) {
            facets.setCategories(
                    response.aggregations().get("leaf_categories").sterms().buckets().array().stream()
                            .map(bucket -> {
                                String categorySlug = bucket.key().stringValue();
                                boolean isSelected = selectedCategories != null &&
                                        selectedCategories.contains(categorySlug);

                                return FacetItem.builder()
                                        .value(categorySlug)
                                        .label(formatCategoryName(categorySlug))
                                        .count(bucket.docCount())
                                        .selected(isSelected)
                                        .build();
                            })
                            .collect(Collectors.toList())
            );
        }

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

    private Map<String, Aggregation> buildAggregations() {
        Map<String, Aggregation> aggregations = new HashMap<>();

        aggregations.put("leaf_categories", Aggregation.of(a -> a
                .terms(t -> t.field("categorySlug").size(100))
        ));

        aggregations.put("brands", Aggregation.of(a -> a
                .terms(t -> t.field("brandSlug").size(100))
        ));

        aggregations.put("sizes", Aggregation.of(a -> a
                .terms(t -> t.field("availableSizes").size(20))
        ));

        aggregations.put("colors", Aggregation.of(a -> a
                .terms(t -> t.field("availableColors").size(50))
        ));

        aggregations.put("price_stats", Aggregation.of(a -> a
                .stats(s -> s.field("basePrice"))
        ));

        return aggregations;
    }

    private String formatCategoryName(String slug) {
        if (slug == null) return "";
        String withoutPrefix = slug.replaceFirst("^(men|women|kids)-", "");
        return Arrays.stream(withoutPrefix.split("-"))
                .map(this::capitalize)
                .collect(Collectors.joining(" "));
    }

    private String formatBrandName(String slug) {
        return capitalize(slug);
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

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

    // CHANGED: Now handles variant-level images from Elasticsearch
    private ProductDTO mapToProductDTO(ProductDocument doc) {
        // Collect all images from all variants (flattened from nested structure)
        List<ProductImageDTO> images = new ArrayList<>();

        if (doc.getVariants() != null) {
            images = doc.getVariants().stream()
                    .filter(variant -> variant.getImages() != null)
                    .flatMap(variant -> variant.getImages().stream())
                    .map(img -> ProductImageDTO.builder()
                            .id(UUID.fromString(img.getId()))
                            .imageUrl(img.getImageUrl())
                            .altText(img.getAltText())
                            .displayOrder(img.getDisplayOrder())
                            .isPrimary(img.getIsPrimary())
                            .build())
                    .collect(Collectors.toList());
        }

        // Fallback: If variants don't have images, check product-level (backward compatibility)
        if (images.isEmpty() && doc.getImages() != null) {
            images = doc.getImages().stream()
                    .map(img -> ProductImageDTO.builder()
                            .id(UUID.fromString(img.getId()))
                            .imageUrl(img.getImageUrl())
                            .altText(img.getAltText())
                            .displayOrder(img.getDisplayOrder())
                            .isPrimary(img.getIsPrimary())
                            .build())
                    .collect(Collectors.toList());
        }

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

                // ✅ MAP VARIANTS (NOT IMAGES)
                .variants(
                        doc.getVariants() == null ? List.of() :
                                doc.getVariants().stream()
                                        .map(v -> ProductVariantDTO.builder()
                                                .id(UUID.fromString(v.getId()))
                                                .size(v.getSize())
                                                .color(v.getColor())
                                                .colorHex(v.getColorHex())
                                                .stockQuantity(v.getStockQuantity())
                                                .additionalPrice(v.getAdditionalPrice())
                                                .isActive(v.getIsActive())

                                                // ✅ IMAGES INSIDE VARIANT
                                                .images(
                                                        v.getImages() == null ? List.of() :
                                                                v.getImages().stream()
                                                                        .map(img -> ProductImageDTO.builder()
                                                                                .id(UUID.fromString(img.getId()))
                                                                                .imageUrl(img.getImageUrl())
                                                                                .altText(img.getAltText())
                                                                                .displayOrder(img.getDisplayOrder())
                                                                                .isPrimary(img.getIsPrimary())
                                                                                .build()
                                                                        )
                                                                        .toList()
                                                )
                                                .build()
                                        )
                                        .toList()
                )

                .averageRating(doc.getAverageRating())
                .reviewCount(doc.getReviewCount())
                .isActive(doc.getIsActive())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();

    }

    public List<String> getAutocomplete(String query, Integer limit) {
        try {
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