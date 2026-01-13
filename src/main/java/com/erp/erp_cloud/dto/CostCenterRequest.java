package com.erp.erp_cloud.dto;


import lombok.Data;

@Data
public class CostCenterRequest {
    private String code;
    private String name;
    private Long parentId;
    private boolean allowsMovement;
    private boolean active = true;
}