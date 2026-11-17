package com.erp.erp_cloud.dto;

import java.util.List;

/**
 * What a chart-of-accounts reset would do for the current company, and
 * whether it is allowed (GET /api/v1/chart-of-accounts/reset-preview).
 *
 * allowed == blockers.isEmpty(). Blockers are things that use the accounts
 * and can't be cleared automatically (posted entries, legacy opening
 * balances, closed periods). documentTypesWithDefaultAccount and
 * companyClosingAccountsSet are configuration that IS cleared
 * automatically and must be set again after re-importing. taxesToDelete:
 * taxes require an account, so they are deleted with the plan (only
 * possible because no entry uses them) and must be created again.
 */
public record ChartOfAccountResetPreviewDTO(
        long accountCount,
        boolean allowed,
        List<String> blockers,
        long documentTypesWithDefaultAccount,
        boolean companyClosingAccountsSet,
        List<String> taxesToDelete
) {
}
