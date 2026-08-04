
package com.erp.erp_cloud.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CategoryOptionDTO {
    private String value;
    private String displayName;
    private String displayNameEs;
    private String accountClass; // e.g. "ASSET" — lets the frontend filter categories by class
}