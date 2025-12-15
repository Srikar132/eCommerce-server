package com.nala.armoire.repository;

import com.nala.armoire.model.document.ProductDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductElasticsearchRepository extends ElasticsearchRepository<ProductDocument, String> {

    Optional<ProductDocument> findBySlug(String slug);

    List<ProductDocument> findByIsActiveTrue();

    boolean existsBySlug(String slug);
}