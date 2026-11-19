package com.erp.erp_cloud.dto;

import java.time.LocalDate;

/**
 * NEW (2026-11-18): whether the current company already has a live
 * (not annulled) APERTURA opening-balance entry. Used by the opening-balance
 * migration screen to warn that migrating again REPLACES that entry
 * completely, including any manual edits made to it afterwards.
 */
public record OpeningBalanceStatusDTO(
        boolean exists,
        String documentNumber,
        LocalDate entryDate,
        int lineCount
) {
    public static OpeningBalanceStatusDTO none() {
        return new OpeningBalanceStatusDTO(false, null, null, 0);
    }
}
