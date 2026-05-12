package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.AccountingPeriodResponseDTO;
import com.erp.erp_cloud.entity.AccountingPeriod;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.exception.ResourceNotFoundException;
import com.erp.erp_cloud.repository.AccountingPeriodRepository;
import com.erp.erp_cloud.security.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AccountingPeriodService {

    private static final Logger log = LoggerFactory.getLogger(AccountingPeriodService.class);

    private final AccountingPeriodRepository repository;
    private final CompanyContext companyContext;

    // ═══════════════════════════════════════════════════════════
    // QUERY METHODS (For Controller)
    // ═══════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<AccountingPeriodResponseDTO> findAllByCompany() {
        Company company = companyContext.getCurrentCompany();
        return repository.findByCompanyOrderByYearDescMonthDesc(company)
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AccountingPeriodResponseDTO> findClosedByCompany() {
        Company company = companyContext.getCurrentCompany();
        return repository.findByCompanyAndOpenFalse(company)
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AccountingPeriodResponseDTO> findOpenByCompany() {
        Company company = companyContext.getCurrentCompany();
        return repository.findByCompanyAndOpenTrue(company)
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<AccountingPeriodResponseDTO> findByYearAndMonth(Integer year, Integer month) {
        Company company = companyContext.getCurrentCompany();
        return repository.findByCompanyAndYearAndMonth(company, year, month)
                .map(this::mapToResponseDTO);
    }

    @Transactional(readOnly = true)
    public boolean isPeriodClosed(Integer year, Integer month) {
        Company company = companyContext.getCurrentCompany();

        // Check Year-End Lock first
        if (repository.existsByCompanyAndYearAndYearCloseTrue(company, year)) {
            return true;
        }

        return repository.findByCompanyAndYearAndMonth(company, year, month)
                .map(p -> !p.isOpen())
                .orElse(false); // If record doesn't exist, it's not closed
    }

    // ═══════════════════════════════════════════════════════════
    // CLOSING LOGIC
    // ═══════════════════════════════════════════════════════════

    /**
     * Standard Monthly Close.
     */
    @Transactional
    public AccountingPeriodResponseDTO closePeriod(Integer year, Integer month, String closedBy, String notes) {
        return performClose(year, month, closedBy, notes, false);
    }

    /**
     * Special Annual Fiscal Close (Locks the whole year).
     */
    @Transactional
    public void closeYear(Integer year, String closedBy, String notes) {
        // We close the last month of the year (12) as a proxy for the year lock
        performClose(year, 12, closedBy, notes, true);
    }

    private AccountingPeriodResponseDTO performClose(Integer year, Integer month, String closedBy, String notes, boolean isYearEnd) {
        Company company = companyContext.getCurrentCompany();
        validateYearMonth(year, month);

        AccountingPeriod period = repository.findByCompanyAndYearAndMonth(company, year, month)
                .orElseGet(() -> {
                    AccountingPeriod newP = new AccountingPeriod();
                    newP.setCompany(company);
                    newP.setYear(year);
                    newP.setMonth(month);
                    return newP;
                });

        period.setOpen(false);
        period.setYearClose(isYearEnd);
        period.setClosedAt(LocalDateTime.now());
        period.setClosedBy(closedBy);
        period.setClosingNotes(notes);

        return mapToResponseDTO(repository.save(period));
    }

    @Transactional
    public AccountingPeriodResponseDTO reopenPeriod(Integer year, Integer month, String reopenedBy, String notes) {
        Company company = companyContext.getCurrentCompany();
        validateYearMonth(year, month);

        AccountingPeriod period = repository.findByCompanyAndYearAndMonth(company, year, month)
                .orElseThrow(() -> new ResourceNotFoundException("Period not found"));

        period.setOpen(true);
        period.setYearClose(false);
        period.setReopenedAt(LocalDateTime.now());
        period.setReopenedBy(reopenedBy);
        period.setReopeningNotes(notes);

        return mapToResponseDTO(repository.save(period));
    }


    /**
     * Reopens a full fiscal year by removing the YearClose seal.
     * This will allow transactions in months that are marked as OPEN.
     */

    @Transactional
    public void reopenYear(Integer year, String reopenedBy, String notes) {
        Company company = companyContext.getCurrentCompany();

        // 1. Get ALL records for that year
        List<AccountingPeriod> yearPeriods = repository.findByCompanyAndYear(company, year);

        // 2. Filter those that have the annual seal and turn it off
        yearPeriods.stream()
                .filter(AccountingPeriod::isYearClose)
                .forEach(period -> {
                    period.setYearClose(false);
                    period.setReopenedAt(LocalDateTime.now());
                    period.setReopenedBy(reopenedBy);
                    period.setReopeningNotes("Annual unseal: " + notes);
                });

        repository.saveAll(yearPeriods);
        log.info("Fiscal Year {} has been unsealed by {}", year, reopenedBy);
    }


    // ═══════════════════════════════════════════════════════════
    // VALIDATION (Used by JournalEntryService)
    // ═══════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public void validateDateIsOpen(LocalDate date, Company company) {
        if (date == null) throw new InvalidOperationException("Date is required");

        int year = date.getYear();
        int month = date.getMonthValue();

        // 1. Check Year Lock
        if (repository.existsByCompanyAndYearAndYearCloseTrue(company, year)) {
            throw new InvalidOperationException("The Fiscal Year " + year + " is CLOSED.");
        }

        // 2. Check Month Lock
        repository.findByCompanyAndYearAndMonth(company, year, month)
                .ifPresent(p -> {
                    if (!p.isOpen()) {
                        throw new InvalidOperationException("The period " + year + "-" + month + " is CLOSED.");
                    }
                });
    }

    // ═══════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════

    private void validateYearMonth(Integer year, Integer month) {
        if (year < 1900 || year > 2100) throw new InvalidOperationException("Invalid year");
        if (month < 1 || month > 12) throw new InvalidOperationException("Invalid month");
    }

    private AccountingPeriodResponseDTO mapToResponseDTO(AccountingPeriod entity) {
        return AccountingPeriodResponseDTO.builder()
                .id(entity.getId())
                .year(entity.getYear())
                .month(entity.getMonth())
                .isOpen(entity.isOpen())
                .isYearClose(entity.isYearClose())
                .closedAt(entity.getClosedAt())
                .closedBy(entity.getClosedBy())
                .build();
    }
}