package com.nala.armoire.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DesignFilterDTO {
    private Long categoryId;
    private String searchTerm;
    private List<String> tags;
    private Boolean isPremium;
    private List<String> productTypes;


    @Builder.Default
    private Integer page = 0;
    @Builder.Default
    private Integer size = 20;

    @Builder.Default
    private String sortBy = "createdAt";

    @Builder.Default
    private String sortDirection = "DESC";
}