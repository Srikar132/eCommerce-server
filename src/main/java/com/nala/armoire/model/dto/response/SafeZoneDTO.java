package com.nala.armoire.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SafeZoneDTO {
    private Integer x;
    private Integer y;
    private Integer width;
    private Integer height;
}
