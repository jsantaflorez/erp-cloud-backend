package com.erp.erp_cloud.dto;



import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor  // Generates a public constructor with no arguments
@AllArgsConstructor
public class ChartOfAccountResponseDTO {
    private Long id;
    private String code;
    private String name;
    private Byte level;
    private String nature;
    private String accountClass;
    private String accountType;
    private boolean postingAccount;
    private boolean requiresThirdParty;
    private boolean requiresCostCenter;
    private boolean active;

    // Calculated fields for the UI
    private String parentCode;
    private String parentName;
    private String fullDescription; // e.g., "110505 - Caja General"
}