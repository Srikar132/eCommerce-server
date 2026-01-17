package com.nala.armoire.repository;

import com.nala.armoire.model.entity.ImageAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for ImageAsset entity
 */
@Repository
public interface ImageAssetRepository extends JpaRepository<ImageAsset, UUID> {
}
