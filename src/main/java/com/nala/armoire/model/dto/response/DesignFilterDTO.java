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
    private Integer page = 0;
    private Integer size = 20;
    private String sortBy = "createdAt";
    private String sortDirection = "DESC";
}