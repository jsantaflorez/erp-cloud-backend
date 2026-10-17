package com.erp.erp_cloud.dto.reports.financial;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One line of the "Informe Detallado de Impuestos y Retenciones"
 * (2026-10-16): a single tax-linked journal entry item, regardless of
 * its Tax category (RETENCION, IVA or OTRO) -- this report replaces
 * what would otherwise be one DTO/service/screen per category.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxDetailLine {

    private LocalDate entryDate;
    private String documentNumber;

    private String thirdPartyDocument;
    private String thirdPartyName;

    /**
     * Line-level detail/glosa -- falls back to the parent voucher's
     * description when the line itself has none, same convention
     * AuxiliaryLedgerService already uses for its own "detail" column.
     */
    private String detail;

    private String accountCode;
    private String accountName;

    /**
     * The Tax's structural category -- RETENCION, IVA or OTRO (see
     * TaxCategory's javadoc). This is what the report's own Categoria
     * filter narrows on, and what subtotaling groups by first.
     */
    private String taxCategory;

    /**
     * The Tax's free-text legal name -- "RETEFUENTE", "RETEICA",
     * "IVA_GENERADO", "IMPOCONSUMO", or any future custom name (see
     * Tax.type's javadoc: never branched on in code, purely
     * descriptive/grouping). Subtotals are built per taxType, nested
     * under taxCategory.
     */
    private String taxType;

    private BigDecimal taxRate;
    private BigDecimal taxBase;

    /**
     * The computed tax/withholding value actually posted -- read
     * directly from whichever side (debit or credit) JournalEntryService
     * .handleTax() populated, never re-derived from the base at report
     * time (see JournalEntryItem.tax's javadoc).
     */
    private BigDecimal taxValue;
}
