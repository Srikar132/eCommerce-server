package com.nala.armoire.service;

import com.nala.armoire.exception.BadRequestException;
import com.nala.armoire.exception.ResourceNotFoundException;
import com.nala.armoire.model.dto.request.CategoryRequest;
import com.nala.armoire.model.dto.response.CategoryDTO;
import com.nala.armoire.model.dto.response.CategoryStatsResponse;
import com.nala.armoire.model.dto.response.PagedResponse;
import com.nala.armoire.model.entity.Category;
import com.nala.armoire.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminCategoryService {

    private static final int SLUG_MAX_LENGTH = 100;
    private static final int NAME_MAX_LENGTH = 500;
    private static final int MAX_SLUG_GENERATION_ATTEMPTS = 100;

    private final CategoryRepository categoryRepository;

    /**
     * Get categories with flexible filtering (non-paginated)
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "adminCategories",
            key = "'list-' + #slug + '-' + #includeChildren + '-' + #recursive + '-' + #minimal + '-' + #includeInactive + '-' + #includeProductCount")
    public List<CategoryDTO> getCategories(
            String slug,
            Boolean includeChildren,
            Boolean recursive,
            Boolean minimal,
            Boolean includeInactive,
            Boolean includeProductCount
    ) {
        List<Category> categories;

        if (slug != null && !slug.isBlank()) {
            // Get specific category by slug
            Category category = categoryRepository.findBySlugWithParent(slug)
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

            if (recursive) {
                // Get category + all descendants recursively
                categories = categoryRepository.findAllDescendants(category.getId());
                categories.add(0, category);
            } else if (includeChildren) {
                // direct children only
                categories = new ArrayList<>();
                List<Category> children = categoryRepository.findByParentIdWithParent(category.getId());
                categories.addAll(children);
            } else {
                // Just the single category
                categories = Collections.singletonList(category);
            }
        } else {
            // Get root categories or full tree
            if (recursive) {
                // Get entire tree (all categories)
                categories = includeInactive
                        ? categoryRepository.findAllWithParent()
                        : categoryRepository.findAllActiveWithParent();
            } else {
                // Get root categories only (no children)
                categories = includeInactive
                        ? categoryRepository.findRootCategoriesWithParent()
                        : categoryRepository.findActiveRootCategoriesWithParent();
            }
        }

        // Enrich with product counts if requested
        if (Boolean.TRUE.equals(includeProductCount)) {
            enrichWithProductCounts(categories);
        }

        // Build tree structure ONLY if recursive=true
        if (recursive) {
            return buildCategoryTree(categories, minimal);
        } else {
            // Flat list - no tree structure, no subcategories array
            return categories.stream()
                    .map(cat -> mapToDto(cat, minimal))
                    .sorted(Comparator.comparing(CategoryDTO::getDisplayOrder, Comparator.nullsLast(Integer::compareTo))
                            .thenComparing(CategoryDTO::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Get paginated categories
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "adminCategoriesPaged",
            key = "'paged-' + #slug + '-' + #includeChildren + '-' + #recursive + '-' + #minimal + '-' + #includeInactive + '-' + #includeProductCount + '-' + #page + '-' + #size + '-' + #sortBy + '-' + #sortDir")
    public PagedResponse<CategoryDTO> getCategoriesPaged(
            String slug,
            Boolean includeChildren,
            Boolean recursive,
            Boolean minimal,
            Boolean includeInactive,
            Boolean includeProductCount,
            Integer page,
            Integer size,
            String sortBy,
            String sortDir
    ) {
        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Category> categoryPage;

        if (slug != null && !slug.isBlank()) {
            Category parent = categoryRepository.findBySlugWithParent(slug)
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
            categoryPage = includeInactive
                    ? categoryRepository.findByParentId(parent.getId(), pageable)
                    : categoryRepository.findActiveByParentId(parent.getId(), pageable);
        } else {
            categoryPage = includeInactive
                    ? categoryRepository.findRootCategories(pageable)
                    : categoryRepository.findActiveRootCategories(pageable);
        }

        List<Category> categories = categoryPage.getContent();

        if (Boolean.TRUE.equals(includeProductCount)) {
            enrichWithProductCounts(categories);
        }

        List<CategoryDTO> dtos = categories.stream()
                .map(cat -> mapToDto(cat, minimal))
                .collect(Collectors.toList());

        // Only add children if includeChildren=true and recursive=false
        if (Boolean.TRUE.equals(includeChildren) && !Boolean.TRUE.equals(recursive)) {
            for (CategoryDTO dto : dtos) {
                List<Category> children = categoryRepository.findByParentIdWithParent(dto.getId());
                if (!children.isEmpty()) {
                    List<CategoryDTO> childDtos = children.stream()
                            .map(c -> mapToDto(c, minimal))
                            .sorted(Comparator.comparing(CategoryDTO::getDisplayOrder, Comparator.nullsLast(Integer::compareTo)))
                            .collect(Collectors.toList());
                    dto.setSubCategories(childDtos);
                }
            }
        }

        return PagedResponse.<CategoryDTO>builder()
                .content(dtos)
                .page(categoryPage.getNumber())
                .size(categoryPage.getSize())
                .totalElements(categoryPage.getTotalElements())
                .totalPages(categoryPage.getTotalPages())
                .first(categoryPage.isFirst())
                .last(categoryPage.isLast())
                .hasNext(categoryPage.hasNext())
                .hasPrevious(categoryPage.hasPrevious())
                .build();
    }

    /**
     * Get single category with hierarchy
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "adminCategoryById", key = "#id + '-' + #includeProductCount")
    public CategoryDTO getCategory(UUID id, Boolean includeProductCount) {
        Category category = getCategoryOrThrow(id);
        CategoryDTO dto = mapToDto(category, false);

        if (Boolean.TRUE.equals(includeProductCount)) {
            Long count = categoryRepository.countProductsByCategoryId(id);
            dto.setProductCount(count != null ? count : 0L);
        }

        List<String> hierarchy = buildHierarchy(category);
        dto.setHierarchy(hierarchy);
        dto.setFullPath(String.join(" / ", hierarchy));

        return dto;
    }

    /**
     * Create root category
     */
    /**
     * Create root category with duplicate validation
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Caching(evict = {
            @CacheEvict(value = "categories", allEntries = true),
            @CacheEvict(value = "categoryBySlug", allEntries = true),
            @CacheEvict(value = "adminCategories", allEntries = true),
            @CacheEvict(value = "adminCategoriesPaged", allEntries = true),
            @CacheEvict(value = "adminCategoryById", allEntries = true)
    })
    public CategoryDTO createRootCategory(CategoryRequest request) {
        validateRequest(request);

        // Check if root category with same name already exists
        if (categoryRepository.existsRootCategoryByNameExcludingId(request.getName(), null)) {
            throw new BadRequestException(
                    String.format("Root category with name '%s' already exists", request.getName())
            );
        }

        // Also check by slug if provided
        if (request.getSlug() != null && !request.getSlug().isBlank()) {
            String normalizedSlug = normalizeSlug(request.getSlug(), request.getName());
            if (categoryRepository.existsBySlugExcludingId(normalizedSlug, null)) {
                throw new BadRequestException(
                        String.format("Category with slug '%s' already exists", normalizedSlug)
                );
            }
        }

        request.setParentId(null);
        return createCategoryInternal(request, null);
    }


    /**
     * Create subcategory with duplicate validation
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Caching(evict = {
            @CacheEvict(value = "categories", allEntries = true),
            @CacheEvict(value = "categoryBySlug", allEntries = true),
            @CacheEvict(value = "adminCategories", allEntries = true),
            @CacheEvict(value = "adminCategoriesPaged", allEntries = true),
            @CacheEvict(value = "adminCategoryById", allEntries = true)
    })
    public CategoryDTO createSubCategory(UUID parentId, CategoryRequest request) {
        validateRequest(request);

        // Verify parent exists
        Category parent = categoryRepository.findById(parentId)
                .orElseThrow(() -> new ResourceNotFoundException("Parent category not found"));

        // Check if subcategory with same name already exists under this parent
        if (categoryRepository.existsSubCategoryByNameAndParentExcludingId(
                request.getName(), parentId, null)) {
            throw new BadRequestException(
                    String.format("Subcategory with name '%s' already exists under parent '%s'",
                            request.getName(), parent.getName())
            );
        }

        // Also check by slug if provided
        if (request.getSlug() != null && !request.getSlug().isBlank()) {
            String normalizedSlug = normalizeSlug(request.getSlug(), request.getName());
            if (categoryRepository.existsBySlugExcludingId(normalizedSlug, null)) {
                throw new BadRequestException(
                        String.format("Category with slug '%s' already exists", normalizedSlug)
                );
            }
        }

        return createCategoryInternal(request, parentId);
    }

    /**
     * Update category with duplicate validation
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @Caching(evict = {
            @CacheEvict(value = "categories", allEntries = true),
            @CacheEvict(value = "categoryBySlug", allEntries = true),
            @CacheEvict(value = "adminCategories", allEntries = true),
            @CacheEvict(value = "adminCategoriesPaged", allEntries = true),
            @CacheEvict(value = "adminCategoryById", key = "#id + '-true'"),
            @CacheEvict(value = "adminCategoryById", key = "#id + '-false'")
    })
    public CategoryDTO updateCategory(UUID id, CategoryRequest request) {
        validateRequest(request);
        Category category = getCategoryOrThrow(id);

        UUID requestedParentId = request.getParentId();

        // Cannot be its own parent
        if (requestedParentId != null && requestedParentId.equals(id)) {
            throw new BadRequestException("Category cannot be its own parent");
        }

        // Validate parent change
        if (requestedParentId != null) {
            validateParentChange(id, requestedParentId);
        }

        Category newParent = resolveParent(requestedParentId);

        // Check for duplicate name
        if (!category.getName().equalsIgnoreCase(request.getName())) {
            if (newParent == null) {
                // Updating to root category - check for duplicate root names
                if (categoryRepository.existsRootCategoryByNameExcludingId(request.getName(), id)) {
                    throw new BadRequestException(
                            String.format("Root category with name '%s' already exists", request.getName())
                    );
                }
            } else {
                // Updating subcategory - check for duplicate names under same parent
                if (categoryRepository.existsSubCategoryByNameAndParentExcludingId(
                        request.getName(), newParent.getId(), id)) {
                    throw new BadRequestException(
                            String.format("Subcategory with name '%s' already exists under parent '%s'",
                                    request.getName(), newParent.getName())
                    );
                }
            }
        }

        // Check for duplicate slug
        String normalizedSlug = normalizeSlug(request.getSlug(), request.getName());
        if (!category.getSlug().equalsIgnoreCase(normalizedSlug)) {
            if (categoryRepository.existsBySlugExcludingId(normalizedSlug, id)) {
                throw new BadRequestException(
                        String.format("Category with slug '%s' already exists", normalizedSlug)
                );
            }
        }

        String uniqueSlug = ensureUniqueSlug(normalizedSlug, id);

        Integer requestedDisplayOrder = request.getDisplayOrder();
        boolean parentChanged = hasParentChanged(category, newParent);
        int displayOrder = requestedDisplayOrder != null
                ? requestedDisplayOrder
                : parentChanged
                ? resolveDisplayOrder(newParent != null ? newParent.getId() : null, null)
                : category.getDisplayOrder();

        category.setName(request.getName());
        category.setSlug(uniqueSlug);
        category.setDescription(request.getDescription());
        category.setImageUrl(request.getImageUrl());
        category.setParent(newParent);
        category.setDisplayOrder(displayOrder);

        if (request.getIsActive() != null) {
            category.setIsActive(request.getIsActive());
        }

        try {
            Category saved = categoryRepository.save(category);
            log.info("Updated category: {}", saved.getId());
            return mapToDto(saved, false);
        } catch (DataIntegrityViolationException e) {
            log.error("Data integrity violation updating category {}: {}", id, e.getMessage());
            throw new BadRequestException("Category update failed: duplicate slug or constraint violation");
        }
    }

    /**
     * Update display order only
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Caching(evict = {
            @CacheEvict(value = "adminCategories", allEntries = true),
            @CacheEvict(value = "adminCategoriesPaged", allEntries = true),
            @CacheEvict(value = "adminCategoryById", key = "#id + '-true'"),
            @CacheEvict(value = "adminCategoryById", key = "#id + '-false'")
    })
    public CategoryDTO updateDisplayOrder(UUID id, Integer newDisplayOrder) {
        Category category = getCategoryOrThrow(id);
        category.setDisplayOrder(newDisplayOrder);
        Category saved = categoryRepository.save(category);
        log.info("Updated display order for category {}: {}", id, newDisplayOrder);
        return mapToDto(saved, false);
    }

    /**
     * Toggle active status
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Caching(evict = {
            @CacheEvict(value = "categories", allEntries = true),
            @CacheEvict(value = "adminCategories", allEntries = true),
            @CacheEvict(value = "adminCategoriesPaged", allEntries = true),
            @CacheEvict(value = "adminCategoryById", key = "#id + '-true'"),
            @CacheEvict(value = "adminCategoryById", key = "#id + '-false'")
    })
    public CategoryDTO toggleStatus(UUID id) {
        Category category = getCategoryOrThrow(id);
        category.setIsActive(!Boolean.TRUE.equals(category.getIsActive()));
        Category saved = categoryRepository.save(category);
        log.info("Toggled category {} status to {}", id, saved.getIsActive());
        return mapToDto(saved, false);
    }

    /**
     * Bulk update status
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Caching(evict = {
            @CacheEvict(value = "categories", allEntries = true),
            @CacheEvict(value = "adminCategories", allEntries = true),
            @CacheEvict(value = "adminCategoriesPaged", allEntries = true),
            @CacheEvict(value = "adminCategoryById", allEntries = true)
    })
    public int bulkUpdateStatus(List<UUID> ids, Boolean active) {
        if (ids == null || ids.isEmpty()) {
            throw new BadRequestException("Category IDs list cannot be empty");
        }

        int updated = categoryRepository.bulkUpdateStatus(ids, active);
        log.info("Bulk updated {} categories to active={}", updated, active);
        return updated;
    }

    /**
     * Delete category
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @Caching(evict = {
            @CacheEvict(value = "categories", allEntries = true),
            @CacheEvict(value = "categoryBySlug", allEntries = true),
            @CacheEvict(value = "adminCategories", allEntries = true),
            @CacheEvict(value = "adminCategoriesPaged", allEntries = true),
            @CacheEvict(value = "adminCategoryById", key = "#id + '-true'"),
            @CacheEvict(value = "adminCategoryById", key = "#id + '-false'")
    })
    public void deleteCategory(UUID id, Boolean force) {
        Category category = getCategoryOrThrow(id);

        if (categoryRepository.existsByParentId(id)) {
            throw new BadRequestException("Cannot delete category that has subcategories");
        }

        Long productCount = categoryRepository.countProductsByCategoryId(id);
        if (productCount != null && productCount > 0) {
            if (!Boolean.TRUE.equals(force)) {
                throw new BadRequestException(
                        String.format("Cannot delete category linked to %d products. Use force=true to cascade delete.", productCount)
                );
            }
            log.warn("Force deleting category {} with {} linked products", id, productCount);
        }

        categoryRepository.delete(category);
        log.info("Deleted category: {}", id);
    }

    /**
     * Get category statistics
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "categoryStats", key = "'stats'")
    public CategoryStatsResponse getCategoryStats() {
        long total = categoryRepository.count();
        long active = categoryRepository.countByIsActive(true);
        long inactive = total - active;
        long roots = categoryRepository.countRootCategories();
        long withProducts = categoryRepository.countCategoriesWithProducts();
        long empty = total - withProducts;
        int maxDepth = categoryRepository.findMaxCategoryDepth();

        return CategoryStatsResponse.builder()
                .totalCategories(total)
                .activeCategories(active)
                .inactiveCategories(inactive)
                .rootCategories(roots)
                .categoriesWithProducts(withProducts)
                .emptyCategoriesCount(empty)
                .maxDepth(maxDepth)
                .build();
    }

    /**
     * Check slug availability
     */
    @Transactional(readOnly = true)
    public boolean isSlugAvailable(String slug, UUID excludeId) {
        String normalized = normalizeSlug(slug, null);
        Optional<Category> existing = categoryRepository.findBySlug(normalized);

        if (existing.isEmpty()) {
            return true;
        }

        return excludeId != null && existing.get().getId().equals(excludeId);
    }

    // ==================== Private Helper Methods ====================

    private CategoryDTO createCategoryInternal(CategoryRequest request, UUID parentIdOverride) {
        Category parent = resolveParent(parentIdOverride != null ? parentIdOverride : request.getParentId());

        String normalizedSlug = normalizeSlug(request.getSlug(), request.getName());
        String uniqueSlug = ensureUniqueSlug(normalizedSlug, null);

        int displayOrder = resolveDisplayOrder(parent != null ? parent.getId() : null, request.getDisplayOrder());

        Category category = Category.builder()
                .name(request.getName())
                .slug(uniqueSlug)
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .parent(parent)
                .displayOrder(displayOrder)
                .isActive(request.getIsActive() == null ? Boolean.TRUE : request.getIsActive())
                .build();

        try {
            Category saved = categoryRepository.save(category);
            log.info("Created category: {} with slug: {}", saved.getId(), saved.getSlug());
            return mapToDto(saved, false);
        } catch (DataIntegrityViolationException e) {
            log.error("Data integrity violation creating category: {}", e.getMessage());
            throw new BadRequestException("Category creation failed: duplicate slug");
        }
    }

    /**
     * Enhanced validation with duplicate checks
     */
    private void validateRequest(CategoryRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new BadRequestException("Category name is required");
        }

        if (request.getName().length() > NAME_MAX_LENGTH) {
            throw new BadRequestException("Category name too long (max " + NAME_MAX_LENGTH + " characters)");
        }

        // Trim whitespace from name
        request.setName(request.getName().trim());

        // Validate slug format if provided
        if (request.getSlug() != null && !request.getSlug().isBlank()) {
            String slug = request.getSlug().trim();

            // Basic slug validation: lowercase, alphanumeric, hyphens only
            if (!slug.matches("^[a-z0-9-]+$")) {
                throw new BadRequestException(
                        "Slug must contain only lowercase letters, numbers, and hyphens"
                );
            }

            if (slug.startsWith("-") || slug.endsWith("-")) {
                throw new BadRequestException("Slug cannot start or end with a hyphen");
            }

            if (slug.contains("--")) {
                throw new BadRequestException("Slug cannot contain consecutive hyphens");
            }

            request.setSlug(slug);
        }
    }

    private Category resolveParent(UUID parentId) {
        if (parentId == null) {
            return null;
        }

        return categoryRepository.findById(parentId)
                .orElseThrow(() -> new ResourceNotFoundException("Parent category not found"));
    }

    private Category getCategoryOrThrow(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }

    private void validateParentChange(UUID categoryId, UUID newParentId) {
        List<UUID> descendantIds = categoryRepository.findAllDescendantIds(categoryId);
        if (descendantIds.contains(newParentId)) {
            throw new BadRequestException("Cannot set a descendant as parent");
        }
    }

    private boolean hasParentChanged(Category category, Category newParent) {
        return !Objects.equals(
                category.getParent() != null ? category.getParent().getId() : null,
                newParent != null ? newParent.getId() : null
        );
    }

    private int resolveDisplayOrder(UUID parentId, Integer requestedDisplayOrder) {
        if (requestedDisplayOrder != null && requestedDisplayOrder >= 0) {
            return requestedDisplayOrder;
        }

        int maxOrder = categoryRepository.findMaxDisplayOrderByParent(parentId);
        return maxOrder + 1;
    }

    private String normalizeSlug(String slugCandidate, String name) {
        String source = (slugCandidate == null || slugCandidate.isBlank()) ? name : slugCandidate;
        if (source == null || source.isBlank()) {
            throw new BadRequestException("Slug or name is required to generate category slug");
        }

        String normalized = Normalizer.normalize(source, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("(^-+|-+$)", "");

        if (normalized.isBlank()) {
            throw new BadRequestException("Generated slug cannot be empty");
        }

        return normalized.length() > SLUG_MAX_LENGTH
                ? normalized.substring(0, SLUG_MAX_LENGTH)
                : normalized;
    }


    /**
     * Enhanced slug uniqueness check
     */
    private String ensureUniqueSlug(String baseSlug, UUID currentCategoryId) {
        String candidate = baseSlug;
        int counter = 1;
        int attempts = 0;

        while (attempts++ < MAX_SLUG_GENERATION_ATTEMPTS) {
            String trimmedCandidate = candidate.length() > SLUG_MAX_LENGTH
                    ? candidate.substring(0, SLUG_MAX_LENGTH)
                    : candidate;

            // Use the new method that excludes current category ID
            if (!categoryRepository.existsBySlugExcludingId(trimmedCandidate, currentCategoryId)) {
                return trimmedCandidate;
            }

            String suffix = "-" + counter++;
            int maxBaseLength = SLUG_MAX_LENGTH - suffix.length();
            String truncatedBase = baseSlug.length() > maxBaseLength
                    ? baseSlug.substring(0, maxBaseLength)
                    : baseSlug;
            candidate = truncatedBase + suffix;
        }

        throw new BadRequestException("Could not generate unique slug after " + MAX_SLUG_GENERATION_ATTEMPTS + " attempts");
    }

    private void enrichWithProductCounts(List<Category> categories) {
        if (categories.isEmpty()) return;

        List<UUID> ids = categories.stream().map(Category::getId).collect(Collectors.toList());
        Map<UUID, Long> counts = categoryRepository.findProductCountsByIds(ids);

        for (Category category : categories) {
            Long count = counts.get(category.getId());
            category.setProductCount(count != null ? count : 0L);
        }
    }

    private List<CategoryDTO> buildCategoryTree(List<Category> categories, Boolean minimal) {
        if (categories.isEmpty()) {
            return Collections.emptyList();
        }

        Map<UUID, CategoryDTO> dtoMap = categories.stream()
                .collect(Collectors.toMap(
                        Category::getId,
                        cat -> mapToDto(cat, minimal),
                        (first, second) -> first,
                        LinkedHashMap::new
                ));

        List<CategoryDTO> roots = new ArrayList<>();

        for (Category category : categories) {
            CategoryDTO dto = dtoMap.get(category.getId());
            UUID parentId = category.getParent() != null ? category.getParent().getId() : null;

            if (parentId == null) {
                roots.add(dto);
            } else {
                CategoryDTO parentDto = dtoMap.get(parentId);
                if (parentDto != null) {
                    if (parentDto.getSubCategories() == null) {
                        parentDto.setSubCategories(new ArrayList<>());
                    }
                    parentDto.getSubCategories().add(dto);
                }
            }
        }

        sortSubCategories(roots);
        populatePaths(roots, new ArrayList<>());

        return roots;
    }

    /**
     * Map Category entity to DTO with proper minimal flag handling
     *
     * @param category - Category entity
     * @param minimal - If true, returns only id, name, slug. If false, returns full data
     * @return CategoryDTO
     */
    private CategoryDTO mapToDto(Category category, Boolean minimal) {
        if (Boolean.TRUE.equals(minimal)) {
            // Minimal DTO: only id, name, slug
            return CategoryDTO.builder()
                    .id(category.getId())
                    .name(category.getName())
                    .slug(category.getSlug())
                    .build();
        }

        // Full DTO
        CategoryDTO.CategoryDTOBuilder builder = CategoryDTO.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .imageUrl(category.getImageUrl())
                .displayOrder(category.getDisplayOrder())
                .isActive(category.getIsActive())
                .createdAt(category.getCreatedAt());

        if (category.getProductCount() != null) {
            builder.productCount(category.getProductCount());
        }

        // Add parent information ONLY if parent exists
        if (category.getParent() != null) {
            Category parent = category.getParent();
            builder.parentId(parent.getId());
            builder.parent(CategoryDTO.ParentCategoryInfo.builder()
                    .id(parent.getId())
                    .name(parent.getName())
                    .slug(parent.getSlug())
                    .parentId(parent.getParent() != null ? parent.getParent().getId() : null)
                    .build());
        }

        return builder.build();
    }

    private void sortSubCategories(List<CategoryDTO> categories) {
        categories.sort(Comparator.comparing(CategoryDTO::getDisplayOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(CategoryDTO::getName, Comparator.nullsLast(String::compareToIgnoreCase)));

        for (CategoryDTO category : categories) {
            if (category.getSubCategories() != null && !category.getSubCategories().isEmpty()) {
                sortSubCategories(category.getSubCategories());
            }
        }
    }

    private void populatePaths(List<CategoryDTO> categories, List<String> currentPath) {
        for (CategoryDTO category : categories) {
            List<String> path = new ArrayList<>(currentPath);
            path.add(category.getName());
            category.setHierarchy(path);
            category.setFullPath(String.join(" / ", path));

            if (category.getSubCategories() != null && !category.getSubCategories().isEmpty()) {
                populatePaths(category.getSubCategories(), path);
            }
        }
    }

    private List<String> buildHierarchy(Category category) {
        LinkedList<String> path = new LinkedList<>();
        Category current = category;

        while (current != null) {
            path.addFirst(current.getName());
            current = current.getParent();
        }

        return new ArrayList<>(path);
    }
}