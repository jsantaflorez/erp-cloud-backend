package com.erp.erp_cloud.dto;

/** Result of a successful POST /api/v1/chart-of-accounts/reset. */
public record ChartOfAccountResetResultDTO(
        long accountsDeleted,
        long documentTypesCleared,
        boolean companyClosingAccountsCleared,
        long taxesDeleted
) {
}
