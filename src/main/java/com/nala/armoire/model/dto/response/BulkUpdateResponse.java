package com.nala.armoire.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BulkUpdateResponse {
    private int updatedCount;
    private String message;
}
