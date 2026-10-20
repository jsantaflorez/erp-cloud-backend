package com.erp.erp_cloud.dto.reports.financial;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One line of the "Informe de Movimientos Incompletos" (2026-10-20,
 * user request): a journal entry line whose account CURRENTLY requires
 * a Third Party and/or a Cost Center that the line doesn't have --
 * almost always because the account was configured loosely at first,
 * movements were posted, and the accountant tightened the account's
 * requirements afterward, leaving those earlier lines "orphaned"
 * (affecting exógena/DIAN reporting and management reports that group
 * by tercero or centro de costo).
 *
 * journalEntryId is exposed (unlike most other report lines, which only
 * expose documentNumber) specifically so the frontend's "Editar" button
 * can jump straight into Asientos Contables and open this exact
 * comprobante for correction, the same way the list view's own Editar
 * button does.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncompleteMovementLine {

    private Long journalEntryId;
    private LocalDate entryDate;
    private String documentNumber;

    private String accountCode;
    private String accountName;

    private boolean missingThirdParty;
    private boolean missingCostCenter;

    /**
     * Whichever side (debit or credit) is actually populated on this
     * line -- same "debit first, else credit" convention
     * TaxDetailReportService uses, since a line only ever has one side
     * set (see JournalEntryItem.validateAmounts()).
     */
    private BigDecimal amount;

    /** "D" or "C" -- which side `amount` came from, for display. */
    private String side;
}
