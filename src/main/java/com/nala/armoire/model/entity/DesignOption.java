package com.nala.armoire.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "design_options")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DesignOption {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "design_id", nullable = false)
    private Design design;

    @Column(name = "option_type", length = 50)
    private String optionType; // COLOR, SIZE, THREAD_TYPE

    @Column(name = "option_value", length = 100)
    private String optionValue;

    @Column(name = "additional_price")
    private Double additionalPrice = 0.0;
}
