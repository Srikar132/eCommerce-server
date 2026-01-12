package com.nala.armoire.model.dto.response;

import com.nala.armoire.model.entity.ImageRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductImageDTO {
    private UUID id;
    private String imageUrl;
    private String altText;
    private Integer displayOrder;
    private Boolean isPrimary;
    private ImageRole imageRole;
}