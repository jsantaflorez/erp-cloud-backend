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
    }
}