package com.erp.erp_cloud.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class JournalEntryResponseDTO {
    private Long id;
    private String documentNumber;
    private Long documentTypeId;
    private LocalDate entryDate;
    private String description;
    private boolean annulled;
    private java.time.LocalDateTime annulledAt;
    private String annulmentReason;
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