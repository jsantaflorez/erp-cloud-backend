package com.erp.erp_cloud.dto;

import com.erp.erp_cloud.enums.AccountCategory;
import com.erp.erp_cloud.enums.AccountClass;
import com.erp.erp_cloud.enums.AccountNature;
import com.erp.erp_cloud.enums.FinancialStatement;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChartOfAccountRequest {

    @NotBlank(message = "Account code is required")
    @Size(max = 20, message = "Code cannot exceed 20 characters")
    private String code;

    @NotBlank(message = "Account name is required")
    @Size(max = 150, message = "Name cannot exceed 150 characters")
    private String name;

    @NotNull(message = "Nature is required (D for Debit, C for Credit)")
    private AccountNature nature;

    @NotNull(message = "Account class is required")
    private AccountClass accountClass;

    @NotNull(message = "Account category is required")
    private AccountCategory accountCategory;

    @NotNull(message = "Financial statement is required")
    private FinancialStatement financialStatement;

    @NotNull(message = "Posting account flag is required")
    private Boolean postingAccount;

    private Boolean requiresThirdParty = false;

    private Boolean requiresCostCenter = false;

    private Boolean active = true;

    private Long parentId;

    // displayOrder and closesAtYearEnd are auto-calculated, not in request
}