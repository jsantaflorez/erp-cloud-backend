package com.erp.erp_cloud.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * NEW (2026-10-03): one row of the Ganancia/Pérdida account dropdown on
 * the year-end closing screen -- just enough to render and select a
 * ChartOfAccounts option, nothing else.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClosingAccountOptionDTO {
    private Long id;
    private String code;
    private String name;
}
