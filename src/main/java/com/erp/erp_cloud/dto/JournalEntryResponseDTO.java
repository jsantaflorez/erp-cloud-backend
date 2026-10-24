package com.erp.erp_cloud.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class JournalEntryResponseDTO {
    private Long id;
    private String documentNumber;
    // NEW (2026-10-24, user request): the voucher's own numeric sequence
    // (JournalEntry.consecutive), exposed alongside documentNumber (which
    // is just "prefix + '-' + consecutive", see
    // JournalEntryService.generateDocNumber()) so a report that needs to
    // find the lowest/highest consecutive USED per document type in a
    // period (e.g. "Consolidado por Tipo de Comprobante") can compare
    // numbers directly instead of string-parsing documentNumber.
    private Long consecutive;
    private Long documentTypeId;
    private LocalDate entryDate;
    private String description;
    private boolean annulled;
    private java.time.LocalDateTime annulledAt;
    private String annulmentReason;
    // NEW (2026-10-22, user-reported UX issue): populated only by
    // JournalEntryService.findById() -- not by the shared mapToResponseDTO
    // path used for list/create/update/annul, to avoid an extra
    // isPeriodClosed() query per row on the list screen. Lets the
    // frontend open the edit form read-only instead of letting the user
    // type a full edit that only fails -- with everything typed lost --
    // once they click Guardar and hit validateDateIsOpen()'s rejection.
    private boolean periodClosed;
    private List<ItemResponse> items;

    @Data
    public static class ItemResponse {
        private Long id;
        private Long accountId;
        private String accountCode;
        private String accountName;
        private BigDecimal debit;
        private BigDecimal credit;
        private String description;
        private Long thirdPartyId;
        private String thirdPartyIdNumber;
        private String thirdPartyName;
        private Long costCenterId;
        private String costCenterName;

        // NEW (2026-10-14, user request): populated only when this
        // line's account is parameterized with a Tax -- see
        // JournalEntryService.handleTax()/mapToResponseDTO(). taxBase is
        // the base gravable the user entered; debit/credit above already
        // carry the computed tax amount.
        private Long taxId;
        private String taxCode;
        private String taxName;
        private String taxType;
        // NEW (2026-10-15): the structural category (RETENCION/IVA/OTRO)
        // this Tax belongs to -- see TaxCategory's javadoc. Planned
        // Fase 3/4 fiscal reports will likely query journal_entry_items
        // joined to taxes directly rather than through this per-entry
        // DTO, but it's cheap to carry here too for anything that reads
        // a posted entry's own response.
        private String taxCategory;
        private BigDecimal taxRate;
        private BigDecimal taxBase;
    }
}