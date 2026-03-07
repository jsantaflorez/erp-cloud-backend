package com.erp.erp_cloud.dto;



import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DocumentTypeRequest {

    @NotBlank(message = "Document code is required")
    @Size(min = 2, max = 10, message = "Code must be between 2 and 10 characters")
    private String code;

    @NotBlank(message = "Document name is required")
    @Size(max = 100, message = "Name cannot exceed 100 characters")
    private String name;

    @Size(max = 5, message = "Prefix cannot exceed 5 characters")
    private String prefix; // No longer @NotBlank

    @NotNull(message = "Must specify if this document affects accounting")
    private Boolean isAccounting;

    @Size(max = 255, message = "Legal resolution details are too long")
    private String legalResolution;
    private Long defaultAccountId;
}