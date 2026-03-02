package com.erp.erp_cloud.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;
/**
 * A generic standardized response wrapper.
 * @param <T> The type of the data payload.
 */


@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // ← This hides 'data' if it is null
public class ApiResponse<T> {
    private String message;
    private boolean success;
    private T data;
    private LocalDateTime timestamp;      // ← NUEVO (opcional)
    private String correlationId;         // ← NUEVO (opcional)

    //
    public ApiResponse(String message, boolean success, T data) {
        this.message = message;
        this.success = success;
        this.data = data;
        this.timestamp = LocalDateTime.now();
        this.correlationId = UUID.randomUUID().toString();
    }

    // Optional auxiliary methods
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(message, true, data);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(message, false, null);
    }
}