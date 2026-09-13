package com.erp.erp_cloud.exception;

import com.erp.erp_cloud.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the 2026-09-10 fix: DuplicateResourceException
 * (thrown by ChartOfAccountService, DocumentTypeService, TaxService and
 * ThirdPartyService whenever a code/document number is already in use)
 * used to reach the frontend as a hardcoded English sentence
 * ("ChartOfAccount already exists with code: 61"), regardless of the
 * app's language -- because handleDuplicateResource() returned
 * ex.getMessage() instead of a stable, translatable code. It now returns
 * DUPLICATE_VALUE, the same code the DB-constraint fallback path
 * (extractConstraintMessage()) already used for this exact situation,
 * which apiErrors.js already translates in both Spanish and English.
 */
class GlobalExceptionHandlerTest {

    @Mock
    private HttpServletRequest request;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        handler = new GlobalExceptionHandler();
        when(request.getRequestURI()).thenReturn("/api/v1/chart-of-accounts");
    }

    @Test
    @DisplayName("handleDuplicateResource() returns the stable DUPLICATE_VALUE code, not the exception's raw English message")
    void handleDuplicateResource_returnsStableCodeNotRawMessage() {
        DuplicateResourceException ex =
                new DuplicateResourceException("ChartOfAccount", "code", "61");

        ApiResponse<Void> body = handler.handleDuplicateResource(ex, request).getBody();

        assertThat(body).isNotNull();
        assertThat(body.getMessage()).isEqualTo("DUPLICATE_VALUE");
        assertThat(body.getMessage()).doesNotContain("ChartOfAccount already exists");
        assertThat(body.isSuccess()).isFalse();
    }
}
