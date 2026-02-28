package com.erp.erp_cloud.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentTypeResponseDTO {

    private Long id;

    /**
     * The unique short code (e.g., "FV")
     */
    private String code;

    /**
     * The descriptive name (e.g., "Factura de Venta")
     */
    private String name;

    /**
     * The optional prefix for document numbering.
     * If present, documents follow 'PREFIX-NUMBER' format.
     */
    private String prefix;
    /**
     * The last number used (to show the user what comes next)
     */
    private Long currentConsecutive;

    /**
     * Flag to indicate if it affects the General Ledger
     */
    private boolean isAccounting;

    /**
     * Status of the document type
     */
    private boolean active;

    /**
     * UI Helper: Combines Code and Name for select inputs.
     * Example: "FV - Factura de Venta"
     */
    private String fullDescription;

    /**
     * UI Helper: Shows how the next document will look.
     * Example: "FV-101"
     */
    private String nextNumberPreview;
}