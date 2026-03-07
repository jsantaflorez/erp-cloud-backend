package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.*;
import com.erp.erp_cloud.entity.*;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.exception.ResourceNotFoundException;
import com.erp.erp_cloud.repository.JournalEntryRepository;
import com.erp.erp_cloud.repository.ChartOfAccountsRepository;
import com.erp.erp_cloud.repository.CostCenterRepository;
import com.erp.erp_cloud.repository.ThirdPartyRepository;
import com.erp.erp_cloud.security.context.CompanyContext;

import java.math.RoundingMode;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JournalEntryService {

    private static final Logger log = LoggerFactory.getLogger(JournalEntryService.class);

    private final JournalEntryRepository repository;
    private final ChartOfAccountsRepository accountRepository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final CostCenterRepository costCenterRepository;
    private final DocumentTypeService docTypeService;
    private final CompanyContext companyContext;
    private final AccountingEngineService accountingEngine;

    /**
     * Creates a new journal entry with automated taxes and balancing.
     */
    @Transactional
    public JournalEntryResponseDTO create(JournalEntryRequest request) {
        Company currentCompany = companyContext.getCurrentCompany();
        log.debug("Creating journal entry for company: {}", currentCompany.getId());

        // 1. Header Validation
        validateEntryDate(request.getEntryDate());
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new InvalidOperationException("Journal entry must contain at least one item.");
        }

        // 2. Document Setup
        DocumentType docType = docTypeService.findById(request.getDocumentTypeId());
        JournalEntry entry = new JournalEntry();
        entry.setDocumentType(docType);
        entry.setEntryDate(request.getEntryDate());
        entry.setDescription(request.getDescription());
        entry.setCompany(currentCompany);

        // Consecutive logic
        Long nextNumber = docTypeService.getNextConsecutive(docType.getId());
        entry.setConsecutive(nextNumber);
        entry.setDocumentNumber(generateDocNumber(docType, nextNumber));

        // 3. PASS 1: Process User Items
        for (int i = 0; i < request.getItems().size(); i++) {
            var itemDto = request.getItems().get(i);
            validateItemAmounts(itemDto.getDebit(), itemDto.getCredit(), i);

            ChartOfAccounts account = fetchAndValidateAccount(itemDto.getAccountId(), currentCompany);

            JournalEntryItem item = new JournalEntryItem();
            item.setAccount(account);
            item.setDebit(Optional.ofNullable(itemDto.getDebit()).orElse(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
            item.setCredit(Optional.ofNullable(itemDto.getCredit()).orElse(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
            item.setDescription(itemDto.getDescription());

            handleThirdParty(item, itemDto, account, currentCompany);
            handleCostCenter(item, itemDto, account, currentCompany);

            entry.addItem(item);
        }

        // 4. PASS 2: Apply System Adjustments (Taxes & Balance)
        applySystemAdjustments(entry, currentCompany);

        // 5. Final Integrity Check
        finalIntegrityCheck(entry);

        return mapToResponseDTO(repository.save(entry));
    }

    /**
     * Logic to calculate taxes and close the accounting gap.
     */
    private void applySystemAdjustments(JournalEntry entry, Company company) {
        BigDecimal runningBalance = BigDecimal.ZERO;
        List<JournalEntryItem> taxLines = new ArrayList<>();

        // Calculate balance from user items and detect taxes
        for (JournalEntryItem item : entry.getItems()) {
            runningBalance = runningBalance.add(item.getDebit()).subtract(item.getCredit());

            BigDecimal baseForTax = item.getDebit().add(item.getCredit());
            TaxCalculationResult taxCheck = accountingEngine.calculateTax(item.getAccount(), baseForTax);

            if (taxCheck.isTaxable() && taxCheck.getTaxAmount().compareTo(BigDecimal.ZERO) > 0) {
                JournalEntryItem taxItem = createAutoTaxItem(taxCheck, item, company);
                taxLines.add(taxItem);
                runningBalance = runningBalance.add(taxItem.getDebit()).subtract(taxItem.getCredit());
            }
        }

        // Add detected tax lines
        taxLines.forEach(entry::addItem);

        // Apply final balancing line if a gap exists
        if (runningBalance.compareTo(BigDecimal.ZERO) != 0) {
            applyBalancingLine(entry, runningBalance, entry.getDocumentType());
        }
    }

    private JournalEntryItem createAutoTaxItem(TaxCalculationResult tax, JournalEntryItem parent, Company company) {
        JournalEntryItem item = new JournalEntryItem();
        ChartOfAccounts taxAccount = accountRepository.findById(tax.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Tax Account", tax.getAccountId()));

        item.setAccount(taxAccount);
        item.setDescription("Auto-tax: " + tax.getTaxName());
        item.setThirdParty(parent.getThirdParty()); // Inherit third party

        BigDecimal amount = tax.getTaxAmount().setScale(2, RoundingMode.HALF_UP);
        if ("D".equalsIgnoreCase(tax.getSign())) {
            item.setDebit(amount);
            item.setCredit(BigDecimal.ZERO);
        } else {
            item.setDebit(BigDecimal.ZERO);
            item.setCredit(amount);
        }
        return item;
    }

    private void applyBalancingLine(JournalEntry entry, BigDecimal runningBalance, DocumentType docType) {
        if (docType.getDefaultAccount() == null) {
            throw new InvalidOperationException("Document " + docType.getCode() + " is unbalanced, but no default account is configured.");
        }

        JournalEntryItem balanceLine = new JournalEntryItem();
        balanceLine.setAccount(docType.getDefaultAccount());
        balanceLine.setDescription("System balance adjustment");

        BigDecimal gap = runningBalance.abs().setScale(2, RoundingMode.HALF_UP);
        if (runningBalance.signum() > 0) { // More debits than credits
            balanceLine.setCredit(gap);
            balanceLine.setDebit(BigDecimal.ZERO);
        } else {
            balanceLine.setDebit(gap);
            balanceLine.setCredit(BigDecimal.ZERO);
        }
        entry.addItem(balanceLine);
    }

    private void finalIntegrityCheck(JournalEntry entry) {
        BigDecimal total = entry.getItems().stream()
                .map(i -> i.getDebit().subtract(i.getCredit()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (total.setScale(2, RoundingMode.HALF_UP).compareTo(BigDecimal.ZERO) != 0) {
            throw new InvalidOperationException("Final document is unbalanced. Difference: " + total);
        }
    }

    private String generateDocNumber(DocumentType docType, Long consecutive) {
        return (docType.getPrefix() != null && !docType.getPrefix().isBlank())
                ? docType.getPrefix().trim() + "-" + consecutive
                : consecutive.toString();
    }

    private ChartOfAccounts fetchAndValidateAccount(Long accountId, Company company) {
        ChartOfAccounts account = accountRepository.findById(accountId)
                .filter(a -> a.getCompany().getId().equals(company.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));

        if (!account.isActive() || !account.isPostingAccount()) {
            throw new InvalidOperationException("Account " + account.getCode() + " is inactive or not a posting account.");
        }
        return account;
    }

    // --- HELPER VALIDATIONS ---

    private void validateEntryDate(LocalDate date) {
        if (date == null) throw new InvalidOperationException("Entry date is required");
        if (date.isAfter(LocalDate.now())) throw new InvalidOperationException("Future dates not allowed");
        if (date.isBefore(LocalDate.now().minusDays(90))) throw new InvalidOperationException("Date too far in the past");
    }

    private void validateItemAmounts(BigDecimal debit, BigDecimal credit, int index) {
        boolean hasDebit = debit != null && debit.compareTo(BigDecimal.ZERO) > 0;
        boolean hasCredit = credit != null && credit.compareTo(BigDecimal.ZERO) > 0;
        if (hasDebit && hasCredit) throw new InvalidOperationException("Item #" + (index+1) + " cannot have both debit and credit.");
        if (!hasDebit && !hasCredit) throw new InvalidOperationException("Item #" + (index+1) + " must have a value.");
    }

    private void handleThirdParty(JournalEntryItem item, JournalEntryRequest.ItemRequest dto, ChartOfAccounts account, Company company) {
        if (account.isRequiresThirdParty()) {
            if (dto.getThirdPartyId() == null) throw new InvalidOperationException("Third party required for " + account.getCode());
            item.setThirdParty(thirdPartyRepository.findById(dto.getThirdPartyId())
                    .filter(t -> t.getCompany().getId().equals(company.getId()))
                    .orElseThrow(() -> new ResourceNotFoundException("ThirdParty", dto.getThirdPartyId())));
        }
    }

    private void handleCostCenter(JournalEntryItem item, JournalEntryRequest.ItemRequest dto, ChartOfAccounts account, Company company) {
        if (account.isRequiresCostCenter()) {
            if (dto.getCostCenterId() == null) throw new InvalidOperationException("Cost center required for " + account.getCode());
            item.setCostCenter(costCenterRepository.findById(dto.getCostCenterId())
                    .filter(c -> c.getCompany().getId().equals(company.getId()))
                    .orElseThrow(() -> new ResourceNotFoundException("CostCenter", dto.getCostCenterId())));
        }
    }

    // --- READ METHODS ---

    public Page<JournalEntryResponseDTO> listEntries(String searchTerm, LocalDate start, LocalDate end, Pageable pageable) {
        return repository.searchEntries(companyContext.getCurrentCompany(), searchTerm, start, end, pageable)
                .map(this::mapToResponseDTO);
    }

    public JournalEntryResponseDTO findByDocumentNumber(String docNum) {
        return repository.findByCompanyAndDocumentNumber(companyContext.getCurrentCompany(), docNum)
                .map(this::mapToResponseDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Journal Entry", docNum));
    }

    @Transactional(readOnly = true)
    public TrialBalanceReport getTrialBalanceReport() {
        Company company = companyContext.getCurrentCompany();
        List<TrialBalanceLine> lines = accountRepository.getTrialBalance(company);

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        for (TrialBalanceLine line : lines) {
            totalDebit = totalDebit.add(line.getTotalDebit());
            totalCredit = totalCredit.add(line.getTotalCredit());
        }


        Map<String, BigDecimal> summary = lines.stream()
                .collect(Collectors.groupingBy(
                        line -> getAccountClassName(line.getAccountCode()),
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                TrialBalanceLine::getNetBalance,
                                BigDecimal::add
                        )
                ));

        boolean isBalanced = totalDebit.setScale(2, RoundingMode.HALF_UP)
                .compareTo(totalCredit.setScale(2, RoundingMode.HALF_UP)) == 0;

        return new TrialBalanceReport(lines, totalDebit, totalCredit, isBalanced, summary);
    }

    /**
     * Helper to translate the first digit of the code into a Category Name
     */
    private String getAccountClassName(String code) {
        char firstDigit = (code != null && !code.isEmpty()) ? code.charAt(0) : '0';
        return switch (firstDigit) {
            case '1' -> "1 - Assets";
            case '2' -> "2 - Liabilities";
            case '3' -> "3 - Equity";
            case '4' -> "4 - Income";
            case '5' -> "5 - Expenses";
            case '6', '7' -> "6/7 - Costs";
            default -> "Other";
        };
    }

    private JournalEntryResponseDTO mapToResponseDTO(JournalEntry entry) {
        JournalEntryResponseDTO dto = new JournalEntryResponseDTO();
        dto.setId(entry.getId());
        dto.setDocumentNumber(entry.getDocumentNumber());
        dto.setEntryDate(entry.getEntryDate());
        dto.setDescription(entry.getDescription());

        dto.setItems(entry.getItems().stream().map(item -> {
            JournalEntryResponseDTO.ItemResponse i = new JournalEntryResponseDTO.ItemResponse();
            i.setId(item.getId());
            i.setAccountCode(item.getAccount().getCode());
            i.setAccountName(item.getAccount().getName());
            i.setDebit(item.getDebit());
            i.setCredit(item.getCredit());
            if (item.getThirdParty() != null) i.setThirdPartyName(item.getThirdParty().getLegalDisplayName());
            if (item.getCostCenter() != null) i.setCostCenterName(item.getCostCenter().getName());
            return i;
        }).toList());

        return dto;
    }
}