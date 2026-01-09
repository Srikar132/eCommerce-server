package com.nala.armoire.exception;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class ErrorResponse {
    private Integer status;
    private String message;
    private String errorCode;
    private LocalDateTime timestamp;
    @Builder.Default
    private Boolean success = false;
    private Map<String, String> details;
}