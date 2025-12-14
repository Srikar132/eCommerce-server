package com.nala.armoire.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CustomizationMetadata {
    public boolean hasText;
    public boolean hasDesign;
    public boolean hasUploadedImage;
    public int layerCount;
}

