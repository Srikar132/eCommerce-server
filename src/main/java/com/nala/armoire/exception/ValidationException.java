package com.nala.armoire.exception;

import com.nala.armoire.model.dto.response.ValidationError;
import lombok.Getter;

import java.util.List;

@Getter
public class ValidationException extends RuntimeException {
    private final List<ValidationError> errors;

    public ValidationException(String message) {
        super(message);
        this.errors = null;
    }

    public ValidationException(String message, List<ValidationError> errors) {
        super(message);
        this.errors = errors;
    }
}
