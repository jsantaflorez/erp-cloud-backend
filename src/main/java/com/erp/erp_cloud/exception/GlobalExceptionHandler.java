package com.erp.erp_cloud.exception;

import com.erp.erp_cloud.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Autowired
    private Environment environment;

    // ═══════════════════════════════════════════════════════════
    // CUSTOM BUSINESS EXCEPTIONS (Highest Priority)
    // ═══════════════════════════════════════════════════════════

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(ex.getMessage(), false, null));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateResource(DuplicateResourceException ex) {
        log.warn("Duplicate resource: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(ex.getMessage(), false, null));
    }

    @ExceptionHandler(InvalidOperationException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidOperation(InvalidOperationException ex) {
        log.warn("Invalid operation: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(ex.getMessage(), false, null));
    }

    // ═══════════════════════════════════════════════════════════
    // VALIDATION EXCEPTIONS
    // ═══════════════════════════════════════════════════════════

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );
        log.warn("Validation failed: {}", errors);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>("Validation failed", false, errors));
    }

    /**
     * Handles IllegalArgumentException thrown from @PrePersist/@PreUpdate validations
     * in entities (e.g., ChartOfAccounts.validateEntity()).
     *
     * This catches validation errors like:
     * - "Financial statement X does not match account class Y"
     * - "Account code cannot be empty"
     * - "Invalid year: must be between 1900 and 2100"
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Entity validation error: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(ex.getMessage(), false, null));
    }

    // ═══════════════════════════════════════════════════════════
    // DATABASE / PERSISTENCE EXCEPTIONS
    // ═══════════════════════════════════════════════════════════

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.error("Database constraint violation", ex);
        String message = extractConstraintMessage(ex);
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(message, false, null));
    }

    /**
     * Handles exceptions thrown from @PrePersist/@PreUpdate that get wrapped
     * by Spring's transaction management.
     *
     * This is CRITICAL for catching entity validation errors that occur
     * during save operations.
     */

    @ExceptionHandler({
            org.springframework.transaction.TransactionSystemException.class,
            jakarta.persistence.PersistenceException.class,
            org.springframework.dao.InvalidDataAccessApiUsageException.class
    })
    public ResponseEntity<ApiResponse<Void>> handlePersistenceException(Exception ex) {
        // Traverse the exception stack to find the root cause (our entity validation)
        Throwable cause = ex;
        while (cause.getCause() != null && !(cause instanceof IllegalArgumentException)) {
            cause = cause.getCause();
        }

        String message = cause.getMessage();
        log.warn("Persistence or Data Access error: {}", message);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(message, false, null));
    }


    // ═══════════════════════════════════════════════════════════
    // HTTP / REQUEST EXCEPTIONS
    // ═══════════════════════════════════════════════════════════

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException ex) {
        log.warn("Missing required header: {}", ex.getHeaderName());
        String message = String.format("The required header '%s' is missing", ex.getHeaderName());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(message, false, null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMalformedJson(HttpMessageNotReadableException ex) {
        log.warn("Malformed JSON request: {}", ex.getMessage());

        // Extract useful information from the error
        String message = "Invalid JSON format or data type mismatch";
        if (ex.getCause() != null) {
            String causeMessage = ex.getCause().getMessage();
            if (causeMessage != null) {
                // Try to extract enum mismatch errors
                if (causeMessage.contains("not one of the values accepted for Enum")) {
                    message = "Invalid enum value. " + causeMessage;
                } else if (causeMessage.contains("Cannot deserialize")) {
                    message = "Cannot deserialize value. Check data types.";
                }
            }
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(message, false, null));
    }

    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(TypeMismatchException ex) {
        String message = String.format("Parameter '%s' expects type %s but received incompatible value",
                ex.getPropertyName(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");

        log.warn("Type mismatch: {}", message);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(message, false, null));
    }

    // ═══════════════════════════════════════════════════════════
    // FALLBACK / CATCH-ALL (Lowest Priority)
    // ═══════════════════════════════════════════════════════════

    /**
     * Catch-all handler for unexpected exceptions.
     * This should be the LAST handler (lowest priority).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex) {
        log.error("Unexpected error occurred", ex);

        // In development, show detailed error
        // In production, show generic message but log details
        String message = isDevelopment()
                ? ex.getMessage()
                : "An unexpected error occurred. Please contact support.";

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(message, false, null));
    }



    // ═══════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════

    private boolean isDevelopment() {
        return Arrays.asList(environment.getActiveProfiles()).contains("dev");
    }

    /**
     * Extracts a user-friendly message from database constraint violations.
     */
    private String extractConstraintMessage(DataIntegrityViolationException ex) {
        String message = "A database constraint was violated.";

        if (ex.getMessage() != null) {
            String exMessage = ex.getMessage().toLowerCase();

            if (exMessage.contains("unique") || exMessage.contains("duplicate")) {
                message = "A record with this value already exists. Please use a different value.";
            } else if (exMessage.contains("foreign key")) {
                message = "Cannot perform this operation due to related records.";
            } else if (exMessage.contains("not null")) {
                message = "A required field is missing.";
            }
        }

        return message;
    }
}