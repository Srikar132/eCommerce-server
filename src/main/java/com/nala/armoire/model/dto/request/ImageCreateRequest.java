package com.nala.armoire.model.dto.request;

import com.nala.armoire.model.entity.ImageRole;
import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageCreateRequest {
    
    @NotBlank(message = "Image URL is required")
    @Size(max = 500)
    private String imageUrl;
    
    @Size(max = 255)
    private String altText;
    
    @Min(0)
    @Builder.Default
    private Integer displayOrder = 0;
    
    @Builder.Default
    private Boolean isPrimary = false;
    
    @NotNull
    @Builder.Default
    private ImageRole imageRole = ImageRole.PREVIEW_BASE;
}
