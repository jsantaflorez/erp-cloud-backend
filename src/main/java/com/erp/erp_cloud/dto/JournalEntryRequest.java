package com.erp.erp_cloud.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class JournalEntryRequest {

    private LocalDate entryDate;
    private Long documentTypeId; // Used to call docTypeService.getNextConsecutive()
    private String description;
    private List<ItemRequest> items;

    @Data
    public static class ItemRequest {
        private Long accountId;
        private Long thirdPartyId;  // Optional
        private Long costCenterId;  // Optional
        private BigDecimal debit = BigDecimal.ZERO;
        private BigDecimal credit = BigDecimal.ZERO;
        private String description;
    }
}