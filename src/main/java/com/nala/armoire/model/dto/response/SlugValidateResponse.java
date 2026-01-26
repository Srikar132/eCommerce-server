package com.nala.armoire.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SlugValidateResponse {

    private String slug;
    private boolean available;
}
