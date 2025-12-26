package com.nala.armoire.repository;

import com.nala.armoire.model.entity.Design;
import com.nala.armoire.model.entity.DesignCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DesignCategoryRepository extends JpaRepository<DesignCategory, UUID> {

    Optional<DesignCategory> findBySlug(String slug);

    List<DesignCategory> findByIsActiveTrueOrderByDisplayOrderAsc();

    @Query("SELECT dc FROM DesignCategory dc " +
            "LEFT JOIN FETCH dc.designs d " +
            "WHERE dc.isActive = true AND d.isActive = true " +
            "ORDER BY dc.displayOrder ASC")
    List<DesignCategory> findAllActiveWithActiveDesigns();
}
