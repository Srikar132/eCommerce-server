package com.nala.armoire.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "design_categories")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DesignCategory {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 50)
    private DesignType type; // EMBROIDERY, HANDCRAFT, PRINT

    @Column(name = "created_at")
    private Date createdAt = new Date();
}