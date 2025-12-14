package com.nala.armoire.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DesignListDTO {
    private UUID id;
    private String name;
    private String thumbnailUrl;
    private List<String> tags;
    private Boolean isPremium;
}
