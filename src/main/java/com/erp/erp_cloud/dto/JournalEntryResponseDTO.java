package com.erp.erp_cloud.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class JournalEntryResponseDTO {
    private Long id;
    private String documentNumber;
    private LocalDate entryDate;
    private String description;
    private List<ItemResponse> items;

    @Data
    public static class ItemResponse {
        private Long id;
        private String accountCode;
        private String accountName;
        private BigDecimal debit;
        private BigDecimal credit;
        private String description;
        private String thirdPartyIdNumber;
        private String thirdPartyName;
        private String costCenterName;
    }
}