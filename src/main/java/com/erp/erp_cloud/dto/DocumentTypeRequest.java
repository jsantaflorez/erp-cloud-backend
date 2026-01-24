package com.erp.erp_cloud.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DocumentTypeRequest {

    @NotBlank(message = "Code is required")
    @Size(max = 10, message = "Code must be up to 10 characters")
    private String code; // Ej: "FV"

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be up to 100 characters")
    private String name; // Ej: "Factura de Venta"

    @Size(max = 10, message = "Prefix must be up to 10 characters")
    private String prefix;

    private Long currentConsecutive; // Optional: start from a specific number

    @NotNull(message = "Accounting flag is required")
    private Boolean isAccounting;

    private String legalResolution;
}