package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.AccountingPeriodResponseDTO;
import com.erp.erp_cloud.entity.AccountingPeriod;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.exception.ResourceNotFoundException;
import com.erp.erp_cloud.repository.AccountingPeriodRepository;
import com.erp.erp_cloud.security.context.TenantContext;
import com.erp.erp_cloud.service.base.TenantAwareService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
public class AccountingPeriodService extends TenantAwareService {

    private static final Logger log = LoggerFactory.getLogger(AccountingPeriodService.class);

    private final AccountingPeriodRepository repository;

    @PersistenceContext
    private final EntityManager entityManager;

    // ═══════════════════════════════════════════════════════════
    // QUERY METHODS (Optimized with Your Original Tenant Context)
    // ═══════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<AccountingPeriodResponseDTO> findAllByCompany() {
        Long companyId = TenantContext.getCurrentTenant(); // FIXED: Matches your context method
        return repository.findByCompanyIdOrderByYearDescMonthDesc(companyId)
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AccountingPeriodResponseDTO> findClosedByCompany() {
        Long companyId = TenantContext.getCurrentTenant(); // FIXED: Matches your context method
        return repository.findByCompanyIdAndOpenFalse(companyId)
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AccountingPeriodResponseDTO> findOpenByCompany() {
        Long companyId = TenantContext.getCurrentTenant(); // FIXED: Matches your context method
        return repository.findByCompanyIdAndOpenTrue(companyId)
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<AccountingPeriodResponseDTO> findByYearAndMonth(Integer year, Integer month) {
        Long companyId = TenantContext.getCurrentTenant(); // FIXED: Matches your context method
        return repository.findByCompanyIdAndYearAndMonth(companyId, year, month)
                .map(this::mapToResponseDTO);
    }

    @Transactional(readOnly = true)
    public boolean isPeriodClosed(Integer year, Integer month) {
        Long companyId = TenantContext.getCurrentTenant(); // FIXED: Matches your context method

        // Check Year-End Lock using optimized derived query (LIMIT 1)
        if (repository.existsByCompanyIdAndYearAndYearCloseTrue(companyId, year)) {
            return true;
        }

        return repository.findByCompanyIdAndYearAndMonth(companyId, year, month)
                .map(p -> !p.isOpen())
                .orElse(false);
    }

    // ═══════════════════════════════════════════════════════════
    // CLOSING LOGIC (Tenant Shield Applied)
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
        performClose(year, 12, closedBy, notes, true);
    }

    private AccountingPeriodResponseDTO performClose(Integer year, Integer month, String closedBy, String notes, boolean isYearEnd) {
        Long companyId = TenantContext.getCurrentTenant(); // FIXED: Matches your context method
        validateYearMonth(year, month);

        AccountingPeriod period = repository.findByCompanyIdAndYearAndMonth(companyId, year, month)
                .orElseGet(() -> {
                    AccountingPeriod newP = new AccountingPeriod();
                    // Avoids heavy select query by creating a lazy-loaded proxy reference using the ID
                    Company proxyCompany = entityManager.getReference(Company.class, companyId);
                    newP.setCompany(proxyCompany);
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
        Long companyId = TenantContext.getCurrentTenant(); // FIXED: Matches your context method
        validateYearMonth(year, month);

        AccountingPeriod period = repository.findByCompanyIdAndYearAndMonth(companyId, year, month)
                .orElseThrow(() -> new ResourceNotFoundException("Period not found under this company"));

        period.setOpen(true);
        period.setYearClose(false);
        period.setReopenedAt(LocalDateTime.now());
        period.setReopenedBy(reopenedBy);
        period.setReopeningNotes(notes);

        return mapToResponseDTO(repository.save(period));
    }

    /**
     * Reopens a full fiscal year by removing the YearClose seal.
     */
    @Transactional
    public void reopenYear(Integer year, String reopenedBy, String notes) {
        Long companyId = TenantContext.getCurrentTenant(); // FIXED: Matches your context method

        List<AccountingPeriod> yearPeriods = repository.findByCompanyIdAndYear(companyId, year);

        yearPeriods.stream()
                .filter(AccountingPeriod::isYearClose)
                .forEach(period -> {
                    period.setYearClose(false);
                    period.setReopenedAt(LocalDateTime.now());
                    period.setReopenedBy(reopenedBy);
                    period.setReopeningNotes("Annual unseal: " + notes);
                });

        repository.saveAll(yearPeriods);
        log.info("Fiscal Year {} has been unsealed by {} for company {}", year, reopenedBy, companyId);
    }

    // ═══════════════════════════════════════════════════════════
    // VALIDATION (Used by JournalEntryService)
    // ═══════════════════════════════════════════════════════════

    /**
     * Validates if a specific transaction date is within an open period.
     */
    @Transactional(readOnly = true)
    public void validateDateIsOpen(LocalDate date, Long companyId) {
        if (date == null) throw new InvalidOperationException("Date is required");

        int year = date.getYear();
        int month = date.getMonthValue();

        if (repository.existsByCompanyIdAndYearAndYearCloseTrue(companyId, year)) {
            throw new InvalidOperationException("The Fiscal Year " + year + " is CLOSED.");
        }

        repository.findByCompanyIdAndYearAndMonth(companyId, year, month)
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