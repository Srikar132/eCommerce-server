package com.nala.armoire.repository;

import com.nala.armoire.model.document.DesignDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

/**
 * Elasticsearch repository for Design search operations
 * Provides fast full-text search, filtering, and aggregations
 */
@Repository
public interface DesignElasticsearchRepository extends ElasticsearchRepository<DesignDocument, String> {
    // Spring Data Elasticsearch provides all basic CRUD operations
    // Custom query methods can be added here if needed
}
