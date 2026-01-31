package com.erp.erp_cloud.service;


import com.erp.erp_cloud.dto.ChartOfAccountRequest;
import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.repository.JournalEntryRepository;
import com.erp.erp_cloud.security.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;


@Service
@Transactional
public class JournalEntryService {

    @Autowired
    private JournalEntryRepository repository;

    @Autowired
    private DocumentTypeRepository docTypeRepository;

    public JournalEntry createEntry(JournalEntryRequest request) {
        // 1. Basic Balance Validation
        validateBalance(request.getItems());

        // 2. Validate Document Type and get Consecutive
        DocumentType docType = docTypeRepository.findById(request.getDocumentTypeId())
                .orElseThrow(() -> new RuntimeException("Document Type not found"));

        // 3. Logic to increment consecutive
        Long nextNumber = docType.getCurrentConsecutive() + 1;
        docType.setCurrentConsecutive(nextNumber);
        docTypeRepository.save(docType);

        // 4. Map DTO to Entity and validate individual items (Cost Center, Third Party)
        // ... (We will implement this next)

        return null; // Placeholder
    }

    private void validateBalance(List<ItemRequest> items) {
        BigDecimal totalDebit = items.stream().map(ItemRequest::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = items.stream().map(ItemRequest::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The entry is not balanced. Debits must equal Credits.");
        }
    }
}