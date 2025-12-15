package com.nala.armoire.listener;

import com.nala.armoire.model.entity.Product;
import com.nala.armoire.service.ProductSyncService;
import jakarta.persistence.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * JPA Entity Listener to automatically sync products to Elasticsearch
 * Whenever a product is created/updated/deleted in PostgreSQL
 */
@Slf4j
@Component
public class ProductEntityListener {

    private static ProductSyncService productSyncService;

    @Autowired
    public void setProductSyncService(ProductSyncService service) {
        ProductEntityListener.productSyncService = service;
    }

    @PostPersist
    public void afterCreate(Product product) {
        log.debug("Product created, syncing to Elasticsearch: {}", product.getId());
        if (productSyncService != null) {
            productSyncService.syncProduct(product.getId());
        }
    }

    @PostUpdate
    public void afterUpdate(Product product) {
        log.debug("Product updated, syncing to Elasticsearch: {}", product.getId());
        if (productSyncService != null) {
            productSyncService.syncProduct(product.getId());
        }
    }

    @PostRemove
    public void afterDelete(Product product) {
        log.debug("Product deleted, removing from Elasticsearch: {}", product.getId());
        if (productSyncService != null) {
            productSyncService.deleteProduct(product.getId());
        }
    }
}