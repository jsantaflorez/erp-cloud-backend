package com.erp.erp_cloud.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Generic standardized response wrapper for all API endpoints.
 * Includes traceability fields (correlationId, path, timestamp)
 * to support frontend error handling and distributed log correlation.
 *
 * @param <T> The type of the data payload.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private String  message;
    private boolean success;
    private T       data;
    private String  path;            // Request URI that originated this response
    private String  correlationId;   // Trace ID — from X-Correlation-ID header or auto-generated
    private Instant timestamp;       // UTC — frontend converts to user's local timezone

    /**
     * Standard constructor — used by success and simple error responses.
     * Auto-generates correlationId and timestamp.
     */
    public ApiResponse(String message, boolean success, T data) {
        this.message       = message;
        this.success       = success;
        this.data          = data;
        this.correlationId = UUID.randomUUID().toString();
        this.timestamp     = Instant.now();
    }

    /**
     * Full constructor — used by GlobalExceptionHandler to include
     * request path and an externally resolved correlationId.
     */
    public ApiResponse(String message, boolean success, T data,
                       String path, String correlationId, Instant timestamp) {
        this.message       = message;
        this.success       = success;
        this.data          = data;
        this.path          = path;
        this.correlationId = correlationId;
        this.timestamp     = timestamp;
    }

    // ── Static factory methods ────────────────────────────────

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(message, true, data);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(message, false, null);
    }
}