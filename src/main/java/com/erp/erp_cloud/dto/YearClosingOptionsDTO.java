package com.erp.erp_cloud.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * NEW (2026-10-03): backs the year-end closing screen's two account
 * dropdowns. equityAccounts is the full picklist (posting accounts of
 * class 3 / Patrimonio only); suggestedGainAccountId/suggestedLossAccountId
 * pre-select an option from Company.defaultGainAccount/defaultLossAccount
 * when configured, or come back null when the company has no default yet
 * (the user must pick explicitly, nothing is force-selected).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YearClosingOptionsDTO {
    private List<ClosingAccountOptionDTO> equityAccounts;
    private Long suggestedGainAccountId;
    private Long suggestedLossAccountId;
}
