package com.erp.erp_cloud.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

@Getter
@AllArgsConstructor
public class ChartOfAccountsMetadataDTO {
    private List<EnumOptionDTO> accountClasses;
    private List<CategoryOptionDTO> accountCategories;
    private List<EnumOptionDTO> financialStatements;
}