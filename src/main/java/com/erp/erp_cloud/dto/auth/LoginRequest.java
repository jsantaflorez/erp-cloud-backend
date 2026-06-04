package com.erp.erp_cloud.dto.auth;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoginRequest(
        @NotBlank(message = "Invalid credentials")
        @Email(message = "Invalid credentials")
        String email,

        @NotBlank(message = "Invalid credentials")
        String password,

        @NotNull(message = "Company ID is required")
        Long companyId
) {}