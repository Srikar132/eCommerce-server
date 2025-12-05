package com.nala.armoire.exception;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ErrorResponse {
    private Integer status;
    private String message;
    private LocalDateTime timestamp;
}