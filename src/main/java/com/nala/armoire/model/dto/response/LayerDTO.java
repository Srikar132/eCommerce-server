package com.nala.armoire.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LayerDTO {
    private String type; // "design", "text", "image"
    private UUID designId;
    private String text;
    private String imageUrl;
    private Integer fontSize;
    private String fontFamily;
    private String color;
    private PositionDTO position;
    private SizeDTO size;
    private Double rotation;
    private Integer zIndex;
}
