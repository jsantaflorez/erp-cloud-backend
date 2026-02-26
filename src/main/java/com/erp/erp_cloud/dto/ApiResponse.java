package com.erp.erp_cloud.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A generic standardized response wrapper.
 * @param <T> The type of the data payload.
 */
@JsonInclude(JsonInclude.Include.NON_NULL) // This hides 'data' if it is null
public record ApiResponse<T>(
        String message,
        boolean success,
        T data
) {
    // Helper constructor for responses without data (like activations)
    public ApiResponse(String message, boolean success) {
        this(message, success, null);
    }
}