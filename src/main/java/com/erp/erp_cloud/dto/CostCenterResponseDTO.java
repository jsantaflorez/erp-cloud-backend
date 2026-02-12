package com.erp.erp_cloud.dto;

import lombok.Data;

@Data
public class CostCenterResponseDTO {
    private Long id;
    private String code;
    private String name;
    private Integer level;
    private boolean active;
    private boolean allowsMovement;
    private Long parentId;
}