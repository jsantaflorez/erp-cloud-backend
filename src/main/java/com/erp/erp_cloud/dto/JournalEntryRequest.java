package com.erp.erp_cloud.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;


@Data
public class JournalEntryRequest {

    @NotNull(message = "Entry date is required")
    private LocalDate entryDate;

    @NotNull(message = "Document Type ID is required")
    private Long documentTypeId;

    @Size(max = 255, message = "Description is too long")
    private String description;

    @NotEmpty(message = "Journal entry must contain at least one item")
    @Valid // This ensures the items inside the list are also validated!
    private List<ItemRequest> items;

    @Data
    public static class ItemRequest {
        @NotNull(message = "Account ID is required")
        private Long accountId;

        private Long thirdPartyId;
        private Long costCenterId;

        @DecimalMin(value = "0.0", inclusive = true)
        private BigDecimal debit = BigDecimal.ZERO;

        @DecimalMin(value = "0.0", inclusive = true)
        private BigDecimal credit = BigDecimal.ZERO;

        @Size(max = 255)
        private String description;

        // NEW (2026-10-14, user request): base gravable for a line whose
        // account is parameterized with a Tax -- see
        // JournalEntryService.handleTax(). Optional: only read when the
        // account actually has a configured Tax; ignored otherwise, same
        // opt-in pattern as thirdPartyId/costCenterId above.
        @DecimalMin(value = "0.0", inclusive = true)
        private BigDecimal taxBase;
    }
    @Data
    public static class AnnulmentRequest {
        @Size(max = 255, message = "Reason is too long")
        @NotEmpty(message = "Annulment reason is required for audit trail")
        private String reason;
    }

    // NEW (2026-10-22, user request): "Duplicar Comprobante" -- copies an
    // existing entry's document type/description/items wholesale onto a
    // brand-new entry at a date the user picks (useful for repetitive
    // entries like nomina/arriendo/depreciacion). Deliberately just the
    // one field -- JournalEntryService.duplicate() builds a full
    // JournalEntryRequest from the source entry and delegates to create(),
    // so the new date gets the exact same validateDateIsOpen()/balance/
    // closing-doc-type checks a hand-typed entry would.
    @Data
    public static class DuplicateRequest {
        @NotNull(message = "Entry date is required")
        private LocalDate entryDate;

        // NEW (2026-10-24, user request): optional override for the
        // description shown/editable in the "Duplicar Comprobante" modal.
        // When present, JournalEntryService.duplicate() uses it for BOTH
        // the new entry's header description AND every line's own
        // description -- replacing whatever each source line had, so the
        // whole voucher carries one consistent note. Left null (e.g. an
        // older client, or any other future caller of this endpoint)
        // preserves the original behavior: header and each line keep
        // their own description exactly as copied from the source entry.
        private String description;
    }

}