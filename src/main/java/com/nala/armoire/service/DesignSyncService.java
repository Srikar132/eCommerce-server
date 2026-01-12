package com.nala.armoire.service;

import com.nala.armoire.model.document.DesignDocument;
import com.nala.armoire.model.entity.Design;
import com.nala.armoire.repository.DesignElasticsearchRepository;
import com.nala.armoire.repository.DesignRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service to sync Design data from PostgreSQL to Elasticsearch
 * Keeps both datastores in sync
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DesignSyncService {

    private final DesignRepository designRepository;
    private final DesignElasticsearchRepository designElasticsearchRepository;

    /**
     * Sync a single design to Elasticsearch
     * Called after CREATE or UPDATE operations
     */
    public void syncDesignToElasticsearch(UUID designId) {
        log.info("Syncing design {} to Elasticsearch", designId);
        
        try {
            Design design = designRepository.findByIdWithCategory(designId)
                    .orElseThrow(() -> new RuntimeException("Design not found: " + designId));

            DesignDocument document = mapToDocument(design);
            designElasticsearchRepository.save(document);
            
            log.info("Successfully synced design {} to Elasticsearch", designId);
        } catch (Exception e) {
            log.error("Failed to sync design {} to Elasticsearch", designId, e);
            // Don't throw - allow the main operation to succeed even if ES sync fails
        }
    }

    /**
     * Sync a design entity to Elasticsearch
     */
    public void syncDesignToElasticsearch(Design design) {
        log.info("Syncing design {} ({}) to Elasticsearch", design.getId(), design.getName());
        
        try {
            DesignDocument document = mapToDocument(design);
            designElasticsearchRepository.save(document);
            
            log.info("Successfully synced design {} to Elasticsearch", design.getId());
        } catch (Exception e) {
            log.error("Failed to sync design {} to Elasticsearch", design.getId(), e);
        }
    }

    /**
     * Delete a design from Elasticsearch
     * Called after DELETE operations
     */
    public void deleteDesignFromElasticsearch(UUID designId) {
        log.info("Deleting design {} from Elasticsearch", designId);
        
        try {
            designElasticsearchRepository.deleteById(designId.toString());
            log.info("Successfully deleted design {} from Elasticsearch", designId);
        } catch (Exception e) {
            log.error("Failed to delete design {} from Elasticsearch", designId, e);
        }
    }

    /**
     * Sync all designs from PostgreSQL to Elasticsearch
     * Use for initial setup or full reindex
     */
    @Transactional(readOnly = true)
    public void syncAllDesigns() {
        log.info("Starting full sync of all designs to Elasticsearch");
        
        try {
            List<Design> allDesigns = designRepository.findAll();
            log.info("Found {} designs in PostgreSQL", allDesigns.size());

            List<DesignDocument> documents = allDesigns.stream()
                    .map(this::mapToDocument)
                    .collect(Collectors.toList());

            designElasticsearchRepository.saveAll(documents);
            
            log.info("Successfully synced {} designs to Elasticsearch", documents.size());
        } catch (Exception e) {
            log.error("Failed to sync all designs to Elasticsearch", e);
            throw new RuntimeException("Full design sync failed", e);
        }
    }

    /**
     * Map Design entity to DesignDocument (Elasticsearch)
     */
    private DesignDocument mapToDocument(Design design) {
        List<String> tagsList = parseTags(design.getTags());
        
        return DesignDocument.builder()
                .id(design.getId().toString())
                .name(design.getName())
                .slug(design.getSlug())
                .description(design.getDescription())
                .designImageUrl(design.getDesignImageUrl())
                .thumbnailUrl(design.getThumbnailUrl())
                .designCategoryId(design.getCategory().getId().toString())
                .designCategorySlug(design.getCategory().getSlug())
                .designCategoryName(design.getCategory().getName())
                .tags(tagsList)
                .tagsText(design.getTags()) // Store original comma-separated tags for text search
                .isActive(design.getIsActive())
                .isPremium(design.getIsPremium())
                .downloadCount(design.getDownloadCount())
                .createdAt(design.getCreatedAt())
                .updatedAt(design.getUpdatedAt())
                .build();
    }

    /**
     * Parse comma-separated tags string into a list
     */
    private List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .collect(Collectors.toList());
    }
}
