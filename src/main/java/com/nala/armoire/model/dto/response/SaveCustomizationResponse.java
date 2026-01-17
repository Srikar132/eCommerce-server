package com.nala.armoire.model.dto.response;

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
public class SaveCustomizationResponse {

    /**
     * The unique customization ID (UUID)
     * Use this to retrieve/update the customization later
     */
    private UUID id;

    /**
     * The preview image URL that was saved
     */
    private String previewImageUrl;

    /**
     * When the customization was created
     */
    private LocalDateTime createdAt;

    /**
     * When the customization was last updated
     * Same as createdAt for new customizations
     */
    private LocalDateTime updatedAt;

    /**
     * Indicates if this was an update (true) or new creation (false)
     * Frontend can use this to show appropriate success message
     */
    private Boolean isUpdate;
}