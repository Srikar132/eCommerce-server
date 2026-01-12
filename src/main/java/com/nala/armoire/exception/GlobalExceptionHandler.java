package com.nala.armoire.exception;

import com.nala.armoire.model.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.nio.file.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ========== Authentication & Security Exceptions ==========
    
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Bad credentials attempt: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.UNAUTHORIZED.value())
                .message("Invalid email or password. Please check your credentials and try again.")
                .errorCode("INVALID_CREDENTIALS")
                .timestamp(LocalDateTime.now())
                .success(false)
                .build();
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }
    
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUsernameNotFound(UsernameNotFoundException ex) {
        log.warn("User not found: {}", ex.getMessage());
        
        // For security, don't reveal if email exists
        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.UNAUTHORIZED.value())
                .message("Invalid email or password. Please check your credentials and try again.")
                .errorCode("INVALID_CREDENTIALS")
                .timestamp(LocalDateTime.now())
                .success(false)
                .build();
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }
    
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabledAccount(DisabledException ex) {
        log.warn("Disabled account access attempt: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.FORBIDDEN.value())
                .message("Your account has been disabled. Please contact support for assistance.")
                .errorCode("ACCOUNT_DISABLED")
                .timestamp(LocalDateTime.now())
                .success(false)
                .build();
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }
    
    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ErrorResponse> handleLockedAccount(LockedException ex) {
        log.warn("Locked account access attempt: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.FORBIDDEN.value())
                .message("Your account has been locked due to multiple failed login attempts. Please try again later or reset your password.")
                .errorCode("ACCOUNT_LOCKED")
                .timestamp(LocalDateTime.now())
                .success(false)
                .build();
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }
    
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        log.error("Authentication error: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.UNAUTHORIZED.value())
                .message("Authentication failed. Please try again.")
                .errorCode("AUTHENTICATION_FAILED")
                .timestamp(LocalDateTime.now())
                .success(false)
                .build();
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    // ========== Application Exceptions ==========

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred", ex);

        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message("Something went wrong. Please try again later.")
                .errorCode("INTERNAL_SERVER_ERROR")
                .timestamp(LocalDateTime.now())
                .success(false)
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(ValidationException ex) {
        log.error("Validation error: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage(), "VALIDATION_ERROR"));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.NOT_FOUND.value())
                .message(ex.getMessage())
                .errorCode("RESOURCE_NOT_FOUND")
                .timestamp(LocalDateTime.now())
                .success(false)
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }


    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
                    .status(HttpStatus.BAD_REQUEST.value())
                    .message(ex.getMessage())
                    .errorCode("BAD_REQUEST")
                    .timestamp(LocalDateTime.now())
                    .success(false)
                    .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex) {
        log.warn("Unauthorized access: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.UNAUTHORIZED.value())
                .message(ex.getMessage())
                .errorCode("UNAUTHORIZED")
                .timestamp(LocalDateTime.now())
                .success(false)
                .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex
    ) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("Validation failed: {}", errors);

        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message("Validation failed. Please check your input.")
                .errorCode("VALIDATION_ERROR")
                .details(errors)
                .timestamp(LocalDateTime.now())
                .success(false)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex
    ) {
        log.error("Access denied: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.FORBIDDEN.value())
                .message("Access denied. You don't have permission to perform this action.")
                .errorCode("ACCESS_DENIED")
                .success(false)
                .build();

        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

}
