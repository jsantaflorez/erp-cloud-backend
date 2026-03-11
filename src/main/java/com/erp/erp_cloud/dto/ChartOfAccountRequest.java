package com.erp.erp_cloud.dto;

import com.erp.erp_cloud.enums.AccountNature;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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



    @NotBlank(message = "Account class is required")
    private String accountClass;

    private String accountType;

    @NotNull(message = "Posting account flag is required")
    private Boolean postingAccount; // true = auxiliary account (allows movements)

    private Boolean requiresThirdParty = false;
    private Boolean requiresCostCenter = false;

    private Boolean active = true;

    // ID of the parent account for hierarchy
    private Long parentId;
}