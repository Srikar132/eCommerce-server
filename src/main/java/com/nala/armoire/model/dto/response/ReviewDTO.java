package com.nala.armoire.model.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDTO {

    private UUID id;
    private UUID userId;
    private String userName;
    private UUID productId;
    private Integer rating;
    private String title;
    private String comment;
    
    @JsonProperty("isVerifiedPurchase")
    private Boolean isVerifiedPurchase;
    
    private LocalDateTime createdAt;
}
