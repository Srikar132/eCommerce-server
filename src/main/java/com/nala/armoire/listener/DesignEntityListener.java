package com.nala.armoire.listener;

import com.nala.armoire.model.entity.Design;
import com.nala.armoire.service.DesignSyncService;
import jakarta.persistence.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * JPA Entity Listener to automatically sync designs to Elasticsearch
 * Whenever a design is created/updated/deleted in PostgreSQL
 */
@Slf4j
@Component
public class DesignEntityListener {

    private static DesignSyncService designSyncService;

    @Autowired
    public void setDesignSyncService(DesignSyncService service) {
        DesignEntityListener.designSyncService = service;
    }

    @PostPersist
    public void afterCreate(Design design) {
        log.debug("Design created, syncing to Elasticsearch: {}", design.getId());
        if (designSyncService != null) {
            designSyncService.syncDesignToElasticsearch(design.getId());
        }
    }

    @PostUpdate
    public void afterUpdate(Design design) {
        log.debug("Design updated, syncing to Elasticsearch: {}", design.getId());
        if (designSyncService != null) {
            designSyncService.syncDesignToElasticsearch(design.getId());
        }
    }

    @PostRemove
    public void afterDelete(Design design) {
        log.debug("Design deleted, removing from Elasticsearch: {}", design.getId());
        if (designSyncService != null) {
            designSyncService.deleteDesignFromElasticsearch(design.getId());
        }
    }
}
