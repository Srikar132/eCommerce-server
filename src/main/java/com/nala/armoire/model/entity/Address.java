package com.nala.armoire.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "addresses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Address {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id" , nullable = false)
    private User user;

    @Column(name = "address_type" , length = 20)
    private String addressType; // HOME , OFFICE , OTHER

    @Column(name = "street_address" , length = 500)
    private String streetAddress;

    @Column(nullable = false , length = 100)
    private String city;

    @Column(nullable = false , length = 100)
    private String state;

    @Column(nullable = false , length = 100)
    private String country;

    @Column(nullable = false)
    private String postalCode;

    @Column(name = "is_default")
    private Boolean isDefault = false;


    @Column(name = "created_at" , updatable = false )
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}


