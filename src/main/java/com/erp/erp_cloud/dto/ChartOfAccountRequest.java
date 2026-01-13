package com.erp.erp_cloud.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChartOfAccountRequest {

    @NotBlank(message = "Account code is required")
    @Size(max = 20)
    private String code;

    @NotBlank(message = "Account name is required")
    @Size(max = 150)
    private String name;

    @NotNull(message = "Level is required")
    private Byte level;

    @NotBlank(message = "Nature (D/C) is required")
    @Size(min = 1, max = 1)
    private String nature;

    @NotBlank(message = "Account class is required")
    private String accountClass;

    private String accountType;

    @NotNull(message = "Posting account flag is required")
    private Boolean postingAccount;

    private Boolean requiresThirdParty = false;
    private Boolean requiresCostCenter = false;
    private Boolean requiresSubCostCenter = false;

    // We only need the ID to establish the hierarchy
    private Long parentId;
}