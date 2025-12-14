package com.nala.armoire.model.dto.request;

import com.nala.armoire.model.dto.response.CustomizationConfigDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomizationRequest {

    private UUID productId;
    private CustomizationConfigDTO configuration;
    private String previewImageUrl;
    private String thumbnailUrl;
    private String sessionId; //for guest users

}

