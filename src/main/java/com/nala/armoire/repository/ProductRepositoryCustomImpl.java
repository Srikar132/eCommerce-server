package com.nala.armoire.repository;

import com.nala.armoire.model.dto.response.FacetItem;
import com.nala.armoire.model.dto.response.PriceRange;
import com.nala.armoire.model.dto.response.ProductFacetsDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Repository
@Slf4j
public class ProductRepositoryCustomImpl implements ProductRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public ProductFacetsDTO getProductFacets(
            List<String> categorySlugs,
            List<String> brandSlugs,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            List<String> sizes,
            List<String> colors,
            Boolean isCustomizable,
            String searchQuery) {

        log.info("Fetching product facets with filters - category: {}, brand: {}, price: {}-{}", 
                categorySlugs, brandSlugs, minPrice, maxPrice);

        // Build the CTE for filtered products
        StringBuilder sql = new StringBuilder();
        sql.append("WITH filtered_products AS ( ");
        sql.append("SELECT DISTINCT p.id, p.category_id, p.brand_id, p.base_price ");
        sql.append("FROM products p ");
        sql.append("WHERE p.is_active = true AND p.is_draft = false ");

        List<Object> parameters = new ArrayList<>();
        int paramIndex = 1;

        // Apply filters to CTE
        if (categorySlugs != null && !categorySlugs.isEmpty()) {
            sql.append("AND EXISTS (SELECT 1 FROM categories c WHERE c.id = p.category_id AND c.slug IN (");
            for (int i = 0; i < categorySlugs.size(); i++) {
                sql.append(i > 0 ? ", " : "").append("?").append(paramIndex++);
                parameters.add(categorySlugs.get(i));
            }
            sql.append(")) ");
        }

        if (brandSlugs != null && !brandSlugs.isEmpty()) {
            sql.append("AND EXISTS (SELECT 1 FROM brands b WHERE b.id = p.brand_id AND b.slug IN (");
            for (int i = 0; i < brandSlugs.size(); i++) {
                sql.append(i > 0 ? ", " : "").append("?").append(paramIndex++);
                parameters.add(brandSlugs.get(i));
            }
            sql.append(")) ");
        }

        if (minPrice != null) {
            sql.append("AND p.base_price >= ?").append(paramIndex++).append(" ");
            parameters.add(minPrice);
        }

        if (maxPrice != null) {
            sql.append("AND p.base_price <= ?").append(paramIndex++).append(" ");
            parameters.add(maxPrice);
        }

        if (isCustomizable != null) {
            sql.append("AND p.is_customizable = ?").append(paramIndex++).append(" ");
            parameters.add(isCustomizable);
        }

        if (sizes != null && !sizes.isEmpty()) {
            sql.append("AND EXISTS (SELECT 1 FROM product_variants pv WHERE pv.product_id = p.id ");
            sql.append("AND pv.is_active = true AND pv.size IN (");
            for (int i = 0; i < sizes.size(); i++) {
                sql.append(i > 0 ? ", " : "").append("?").append(paramIndex++);
                parameters.add(sizes.get(i));
            }
            sql.append(")) ");
        }

        if (colors != null && !colors.isEmpty()) {
            sql.append("AND EXISTS (SELECT 1 FROM product_variants pv WHERE pv.product_id = p.id ");
            sql.append("AND pv.is_active = true AND LOWER(pv.color) IN (");
            for (int i = 0; i < colors.size(); i++) {
                sql.append(i > 0 ? ", " : "").append("?").append(paramIndex++);
                parameters.add(colors.get(i).toLowerCase());
            }
            sql.append(")) ");
        }

        if (searchQuery != null && !searchQuery.trim().isEmpty()) {
            sql.append("AND (LOWER(p.name) LIKE ?").append(paramIndex++);
            sql.append(" OR LOWER(p.description) LIKE ?").append(paramIndex++).append(") ");
            String searchPattern = "%" + searchQuery.toLowerCase().trim() + "%";
            parameters.add(searchPattern);
            parameters.add(searchPattern);
        }

        sql.append(") ");

        // Build facet queries
        sql.append("SELECT ");
        
        // Category facets
        sql.append("(SELECT json_agg(json_build_object('value', c.slug, 'label', c.name, 'count', cnt)) ");
        sql.append("FROM (SELECT c.id, c.slug, c.name, COUNT(fp.id) as cnt ");
        sql.append("FROM categories c ");
        sql.append("LEFT JOIN filtered_products fp ON fp.category_id = c.id ");
        sql.append("WHERE c.is_active = true ");
        sql.append("GROUP BY c.id, c.slug, c.name ");
        sql.append("HAVING COUNT(fp.id) > 0 ");
        sql.append("ORDER BY cnt DESC) c) AS categories, ");

        // Brand facets
        sql.append("(SELECT json_agg(json_build_object('value', b.slug, 'label', b.name, 'count', cnt)) ");
        sql.append("FROM (SELECT b.id, b.slug, b.name, COUNT(fp.id) as cnt ");
        sql.append("FROM brands b ");
        sql.append("LEFT JOIN filtered_products fp ON fp.brand_id = b.id ");
        sql.append("WHERE b.is_active = true ");
        sql.append("GROUP BY b.id, b.slug, b.name ");
        sql.append("HAVING COUNT(fp.id) > 0 ");
        sql.append("ORDER BY cnt DESC) b) AS brands, ");

        // Size facets
        sql.append("(SELECT json_agg(json_build_object('value', pv.size, 'label', pv.size, 'count', cnt)) ");
        sql.append("FROM (SELECT pv.size, COUNT(DISTINCT fp.id) as cnt ");
        sql.append("FROM product_variants pv ");
        sql.append("INNER JOIN filtered_products fp ON pv.product_id = fp.id ");
        sql.append("WHERE pv.is_active = true AND pv.stock_quantity > 0 ");
        sql.append("GROUP BY pv.size ");
        sql.append("ORDER BY CASE pv.size ");
        sql.append("WHEN 'XS' THEN 1 WHEN 'S' THEN 2 WHEN 'M' THEN 3 ");
        sql.append("WHEN 'L' THEN 4 WHEN 'XL' THEN 5 WHEN 'XXL' THEN 6 ");
        sql.append("ELSE 7 END) pv) AS sizes, ");

        // Color facets
        sql.append("(SELECT json_agg(json_build_object('value', pv.color, 'label', pv.color, 'count', cnt, 'colorHex', pv.color_hex)) ");
        sql.append("FROM (SELECT pv.color, pv.color_hex, COUNT(DISTINCT fp.id) as cnt ");
        sql.append("FROM product_variants pv ");
        sql.append("INNER JOIN filtered_products fp ON pv.product_id = fp.id ");
        sql.append("WHERE pv.is_active = true AND pv.color IS NOT NULL ");
        sql.append("GROUP BY pv.color, pv.color_hex ");
        sql.append("ORDER BY cnt DESC) pv) AS colors, ");

        // Price range
        sql.append("(SELECT json_build_object('min', COALESCE(MIN(base_price), 0), 'max', COALESCE(MAX(base_price), 0)) ");
        sql.append("FROM filtered_products) AS price_range, ");

        // Total products
        sql.append("(SELECT COUNT(*) FROM filtered_products) AS total_products");

        Query query = entityManager.createNativeQuery(sql.toString());
        
        // Set parameters
        for (int i = 0; i < parameters.size(); i++) {
            query.setParameter(i + 1, parameters.get(i));
        }

        Object[] result = (Object[]) query.getSingleResult();

        ProductFacetsDTO facets = new ProductFacetsDTO();
        
        // Parse categories
        facets.setCategories(parseFacetItems((String) result[0]));
        
        // Parse brands
        facets.setBrands(parseFacetItems((String) result[1]));
        
        // Parse sizes
        facets.setSizes(parseFacetItems((String) result[2]));
        
        // Parse colors
        facets.setColors(parseFacetItems((String) result[3]));
        
        // Parse price range
        facets.setPriceRange(parsePriceRange((String) result[4]));
        
        // Parse total products
        facets.setTotalProducts(((Number) result[5]).longValue());

        log.info("Facets retrieved - categories: {}, brands: {}, sizes: {}, colors: {}, total: {}", 
                facets.getCategories() != null ? facets.getCategories().size() : 0,
                facets.getBrands() != null ? facets.getBrands().size() : 0,
                facets.getSizes() != null ? facets.getSizes().size() : 0,
                facets.getColors() != null ? facets.getColors().size() : 0,
                facets.getTotalProducts());

        return facets;
    }

    @Override
    public List<String> findProductNameAutocomplete(String query, Integer limit) {
        String sql = "SELECT DISTINCT p.name FROM products p " +
                     "WHERE p.is_active = true AND p.is_draft = false " +
                     "AND LOWER(p.name) LIKE ?1 " +
                     "ORDER BY p.name " +
                     "LIMIT ?2";

        Query nativeQuery = entityManager.createNativeQuery(sql);
        nativeQuery.setParameter(1, "%" + query.toLowerCase() + "%");
        nativeQuery.setParameter(2, limit);

        @SuppressWarnings("unchecked")
        List<String> results = nativeQuery.getResultList();
        return results;
    }

    /**
     * Parse JSON array string to List of FacetItem
     */
    private List<FacetItem> parseFacetItems(String jsonArray) {
        if (jsonArray == null || jsonArray.equals("null")) {
            return new ArrayList<>();
        }

        List<FacetItem> items = new ArrayList<>();
        
        // Simple JSON parsing (you might want to use Jackson for production)
        jsonArray = jsonArray.trim();
        if (!jsonArray.startsWith("[") || !jsonArray.endsWith("]")) {
            return items;
        }

        jsonArray = jsonArray.substring(1, jsonArray.length() - 1);
        if (jsonArray.trim().isEmpty()) {
            return items;
        }

        // Split by objects
        String[] objects = jsonArray.split("\\},\\s*\\{");
        
        for (String obj : objects) {
            obj = obj.replace("{", "").replace("}", "").trim();
            
            FacetItem item = new FacetItem();
            String[] fields = obj.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"); // Split by comma not inside quotes
            
            for (String field : fields) {
                String[] keyValue = field.split(":", 2);
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim().replace("\"", "");
                    String value = keyValue[1].trim().replace("\"", "");
                    
                    switch (key) {
                        case "value":
                            item.setValue(value);
                            break;
                        case "label":
                            item.setLabel(value);
                            break;
                        case "count":
                            item.setCount(Long.parseLong(value));
                            break;
                        case "colorHex":
                            if (!value.equals("null")) {
                                item.setColorHex(value);
                            }
                            break;
                    }
                }
            }
            items.add(item);
        }
        
        return items;
    }

    /**
     * Parse JSON object string to PriceRange
     */
    private PriceRange parsePriceRange(String jsonObject) {
        if (jsonObject == null || jsonObject.equals("null")) {
            return new PriceRange(BigDecimal.ZERO, BigDecimal.ZERO);
        }

        jsonObject = jsonObject.trim().replace("{", "").replace("}", "");
        String[] fields = jsonObject.split(",");
        
        BigDecimal min = BigDecimal.ZERO;
        BigDecimal max = BigDecimal.ZERO;
        
        for (String field : fields) {
            String[] keyValue = field.split(":", 2);
            if (keyValue.length == 2) {
                String key = keyValue[0].trim().replace("\"", "");
                String value = keyValue[1].trim();
                
                if (key.equals("min")) {
                    min = new BigDecimal(value);
                } else if (key.equals("max")) {
                    max = new BigDecimal(value);
                }
            }
        }
        
        return new PriceRange(min, max);
    }
}
