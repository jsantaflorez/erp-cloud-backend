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
    }
    @Data
    public class AnnulmentRequest {
        @Size(max = 255, message = "Reason is too long")
        @NotEmpty(message = "Annulment reason is required for audit trail")
        private String reason;
    }

}