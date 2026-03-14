package com.erp.erp_cloud.dto;

import com.erp.erp_cloud.enums.AccountCategory;
import com.erp.erp_cloud.enums.AccountClass;
import com.erp.erp_cloud.enums.AccountNature;
import com.erp.erp_cloud.enums.FinancialStatement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChartOfAccountResponseDTO {

    private Long id;
    private String code;
    private String name;
    private Byte level;
    private AccountNature nature;

    // Enhanced fields
    private AccountClass accountClass;
    private AccountCategory accountCategory;
    private String accountCategoryDisplay;  // Human-readable

    private FinancialStatement financialStatement;
    private String financialStatementDisplay;  // Human-readable

    private boolean closesAtYearEnd;
    private Integer displayOrder;

    // Business rules
    private boolean postingAccount;
    private boolean requiresThirdParty;
    private boolean requiresCostCenter;
    private boolean active;

    // Hierarchy
    private String parentCode;
    private String parentName;

    // UI helper
    private String fullDescription;
}