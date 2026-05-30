package com.erp.erp_cloud.exception;

import com.erp.erp_cloud.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
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

import java.time.Instant;
import java.util.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Autowired
    private Environment environment;

    // ═══════════════════════════════════════════════════════════
    // CUSTOM BUSINESS EXCEPTIONS (Highest Priority)
    // ═══════════════════════════════════════════════════════════

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(buildError(ex.getMessage(), request));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateResource(DuplicateResourceException ex, HttpServletRequest request) {
        log.warn("Duplicate resource: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(buildError(ex.getMessage(), request));
    }

    @ExceptionHandler(InvalidOperationException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidOperation(InvalidOperationException ex, HttpServletRequest request) {
        log.warn("Invalid operation: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildError(ex.getMessage(), request));
    }

    // ═══════════════════════════════════════════════════════════
    // VALIDATION EXCEPTIONS
    // ═══════════════════════════════════════════════════════════

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );
        log.warn("Validation failed: {}", errors);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildError("Validation failed", request, errors));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Entity validation error: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildError(ex.getMessage(), request));
    }

    // ═══════════════════════════════════════════════════════════
    // SPRING SECURITY & AUTH EXCEPTIONS (Binds to Phase 2)
    // ═══════════════════════════════════════════════════════════

    @ExceptionHandler(org.springframework.security.authentication.LockedException.class)
    public ResponseEntity<ApiResponse<Void>> handleLockedException(org.springframework.security.authentication.LockedException ex, HttpServletRequest request) {
        log.warn("Access Denied | Account is temporarily locked: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(buildError("Unauthorized identity criteria match rejected.", request));
    }

    @ExceptionHandler({
            org.springframework.security.core.userdetails.UsernameNotFoundException.class,
            org.springframework.security.authentication.BadCredentialsException.class,
            org.springframework.security.authentication.DisabledException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationExceptions(Exception ex, HttpServletRequest request) {
        log.warn("Authentication entry rejected | Security Reason: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(buildError("Unauthorized identity criteria match rejected.", request));
    }

    // ═══════════════════════════════════════════════════════════
    // DATABASE / PERSISTENCE EXCEPTIONS
    // ═══════════════════════════════════════════════════════════

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.error("Database constraint violation", ex);
        String message = extractConstraintMessage(ex);
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(buildError(message, request));
    }

    @ExceptionHandler({
            org.springframework.transaction.TransactionSystemException.class,
            jakarta.persistence.PersistenceException.class,
            org.springframework.dao.InvalidDataAccessApiUsageException.class
    })
    public ResponseEntity<ApiResponse<Void>> handlePersistenceException(Exception ex, HttpServletRequest request) {
        Throwable cause = ex;
        while (cause.getCause() != null && !(cause instanceof IllegalArgumentException)) {
            cause = cause.getCause();
        }

        String message = cause.getMessage();
        log.warn("Persistence or Data Access error: {}", message);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildError(message, request));
    }

    // ═══════════════════════════════════════════════════════════
    // HTTP / REQUEST EXCEPTIONS
    // ═══════════════════════════════════════════════════════════

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        log.warn("Missing required header: {}", ex.getHeaderName());
        String message = String.format("The required header '%s' is missing", ex.getHeaderName());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildError(message, request)); // ✅ Corregido con trazabilidad
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMalformedJson(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Malformed JSON request: {}", ex.getMessage());

        String message = "Invalid JSON format or data type mismatch";
        if (ex.getCause() != null) {
            String causeMessage = ex.getCause().getMessage();
            if (causeMessage != null) {
                if (causeMessage.contains("not one of the values accepted for Enum")) {
                    message = "Invalid enum value. " + causeMessage;
                } else if (causeMessage.contains("Cannot deserialize")) {
                    message = "Cannot deserialize value. Check data types.";
                }
            }
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildError(message, request)); // ✅ Corregido con trazabilidad
    }

    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(TypeMismatchException ex, HttpServletRequest request) {
        String message = String.format("Parameter '%s' expects type %s but received incompatible value",
                ex.getPropertyName(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");

        log.warn("Type mismatch: {}", message);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildError(message, request)); // ✅ Corregido con trazabilidad
    }

    // ═══════════════════════════════════════════════════════════
    // FALLBACK / CATCH-ALL (Lowest Priority)
    // ═══════════════════════════════════════════════════════════

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error occurred", ex);

        String message = isDevelopment()
                ? ex.getMessage()
                : "An unexpected error occurred. Please contact support.";

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError(message, request)); // ✅ Corregido con trazabilidad
    }

    // ═══════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════

    private ApiResponse<Void> buildError(String message, HttpServletRequest request) {
        String correlationId = Optional
                .ofNullable(request.getHeader("X-Correlation-ID"))
                .orElse(UUID.randomUUID().toString());

        return new ApiResponse<>(message, false, null,
                request.getRequestURI(),
                correlationId,
                Instant.now());
    }

    private <T> ApiResponse<T> buildError(String message, HttpServletRequest request, T data) {
        String correlationId = Optional
                .ofNullable(request.getHeader("X-Correlation-ID"))
                .orElse(UUID.randomUUID().toString());

        return new ApiResponse<>(message, false, data,
                request.getRequestURI(),
                correlationId,
                Instant.now());
    }

    private boolean isDevelopment() {
        return Arrays.asList(environment.getActiveProfiles()).contains("dev");
    }

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