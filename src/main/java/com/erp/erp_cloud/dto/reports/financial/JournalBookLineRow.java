package com.erp.erp_cloud.dto.reports.financial;

import java.math.BigDecimal;

/**
 * Fase 4 (2026-11-24): one journal entry line, read with a JPQL
 * constructor projection (account, third party and cost center joined in
 * the same query -- no N+1). The third party's display name is built in
 * Java from its raw name fields (ThirdParty.legalDisplayName).
 */
public record JournalBookLineRow(
        Long entryId,
        Long id,
        String accountCode,
        String accountName,
        String thirdPartyDocument,
        String thirdPartyFirstName,
        String thirdPartyMiddleName,
        String thirdPartyLastName,
        String thirdPartySecondLastName,
        String thirdPartyBusinessName,
        String thirdPartyTradeName,
        String costCenterName,
        BigDecimal debit,
        BigDecimal credit,
        String description) {
}
