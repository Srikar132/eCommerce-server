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
     * Get products with filters and including facets for them
     */
    public ProductSearchResponse getProducts(
            List<String> categorySlugs,
            List<String> brandSlugs,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            List<String> sizes,
            List<String> colors,
            Boolean isCustomizable,
            String searchQuery, Pageable pageable
    ) {
        try{
            BoolQuery.Builder boolQuery = new BoolQuery.Builder();

            boolQuery.must(m -> m.term(t -> t.field("isActive").value(true)));

            if(categorySlugs != null && !categorySlugs.isEmpty()) {
                boolQuery.filter(f -> f.terms(t -> t
                        .field("categorySlug")
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
                                .map(s -> FieldValue.of(s))
                                .collect(Collectors.toList())))
                ));
            }





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

            if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                boolQuery.must(m -> m.multiMatch(mm -> mm
                        .query(searchQuery)
                        .fields("name^3", "description^2", "material")
                        .type(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.BestFields)
                        .fuzziness("AUTO")
                ));
            }

            Map<String, Aggregation> aggregations = buildAggregations();

            // Parse sorting from Pageable
            List<co.elastic.clients.elasticsearch._types.SortOptions> sortOptions = parseSortFromPageable(pageable);

            // Build and execute search request
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
            ProductFacetsDTO facets = extractFacets(response);

            // Get total hits
            long totalHits = response.hits().total() != null ? response.hits().total().value() : 0;

            // Build paged response
            PagedResponse<ProductDTO> pagedProducts = PagedResponse.<ProductDTO>builder()
                    .content(products)
                    .page(pageable.getPageNumber() + 1) // Pageable is 0-based, convert to 1-based
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

        }catch(Exception e){
            log.error("Error searching products", e);
            throw new RuntimeException("Failed to search products", e);
        }
    }


    /**
     * Extract facets from search response
     */
    private ProductFacetsDTO extractFacets(SearchResponse<ProductDocument> response) {
        ProductFacetsDTO facets = new ProductFacetsDTO();

        // Extract category facets
        if (response.aggregations().containsKey("categories")) {
            facets.setCategories(
                    response.aggregations().get("categories").sterms().buckets().array().stream()
                            .map(bucket -> FacetItem.builder()
                                    .value(bucket.key().stringValue())
                                    .label(bucket.key().stringValue())
                                    .count(bucket.docCount())
                                    .build())
                            .collect(Collectors.toList())
            );
        }

        // Extract brand facets
        if (response.aggregations().containsKey("brands")) {
            facets.setBrands(
                    response.aggregations().get("brands").sterms().buckets().array().stream()
                            .map(bucket -> FacetItem.builder()
                                    .value(bucket.key().stringValue())
                                    .label(bucket.key().stringValue())
                                    .count(bucket.docCount())
                                    .build())
                            .collect(Collectors.toList())
            );
        }

        // Extract size facets
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

        // Extract color facets
        if (response.aggregations().containsKey("colors")) {
            facets.setColors(
                    response.aggregations().get("colors").sterms().buckets().array().stream()
                            .map(bucket -> FacetItem.builder()
                                    .value(bucket.key().stringValue())
                                    .label(bucket.key().stringValue())
                                    .count(bucket.docCount())
                                    .build())
                            .collect(Collectors.toList())
            );
        }

        // Extract price range - FIX IS HERE
        if (response.aggregations().containsKey("price_stats")) {
            var stats = response.aggregations().get("price_stats").stats();

            // CRITICAL CHECK: Only convert if count > 0 to avoid "Infinity" error
            if (stats.count() > 0) {
                facets.setPriceRange(PriceRange.builder()
                        .min(BigDecimal.valueOf(stats.min()))
                        .max(BigDecimal.valueOf(stats.max()))
                        .build());
            } else {
                // Return 0s if no products found
                facets.setPriceRange(PriceRange.builder()
                        .min(BigDecimal.ZERO)
                        .max(BigDecimal.ZERO)
                        .build());
            }
        }

        return facets;
    }


    /**
     * Build aggregations for facets
     */
    private Map<String, Aggregation> buildAggregations() {
        Map<String, Aggregation> aggregations = new HashMap<>();

        // Category aggregation
        aggregations.put("categories", Aggregation.of(a -> a
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
     * Parse sorting from Spring Pageable
     */
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
            // Default sort by createdAt DESC
            sortOptions.add(co.elastic.clients.elasticsearch._types.SortOptions.of(
                    s -> s.field(f -> f.field("createdAt").order(SortOrder.Desc))
            ));
        }

        return sortOptions;
    }


    /**
     * Map ProductDocument to ProductDTO
     */
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
 }
