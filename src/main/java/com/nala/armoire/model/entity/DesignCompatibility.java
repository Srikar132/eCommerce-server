package com.nala.armoire.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "design_compatibility")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DesignCompatibility {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "design_id", nullable = false)
    private Design design;

    @ElementCollection
    @CollectionTable(
            name = "allowed_positions",
            joinColumns = @JoinColumn(name = "compatibility_id")
    )
    @Column(name = "position")
    private List<String> allowedPositions = List.of();
}
