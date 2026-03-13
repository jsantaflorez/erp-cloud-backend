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

@Service
@RequiredArgsConstructor
@Transactional
public class AccountingPeriodService {

    private static final Logger log = LoggerFactory.getLogger(AccountingPeriodService.class);

    private final AccountingPeriodRepository repository;
    private final CompanyContext companyContext;

    // ============================================
    // VALIDATION METHODS (Used by JournalEntry)
    // ============================================

    /**
     * Validates if a specific date belongs to an open accounting period.
     * This is the main "bouncer" method called before creating/updating journal entries.
     *
     * BUSINESS RULE: If a period doesn't exist in the database, it's assumed to be OPEN.
     * This allows companies to work in future periods without pre-creating them.
     *
     * @param date The transaction date to validate
     * @param company The company context
     * @throws InvalidOperationException if the period is closed
     */
    @Transactional(readOnly = true)
    public void validateDateIsOpen(LocalDate date, Company company) {
        if (date == null) {
            throw new InvalidOperationException("Transaction date cannot be null");
        }

        int year = date.getYear();
        int month = date.getMonthValue();

        log.debug("Validating period {}-{:02d} for company: {}", year, month, company.getId());

        Optional<AccountingPeriod> periodOpt = repository.findByCompanyAndYearAndMonth(company, year, month);

        if (periodOpt.isPresent()) {
            AccountingPeriod period = periodOpt.get();
            if (!period.isOpen()) {
                log.warn("Attempt to post to CLOSED period {}-{:02d} by company: {}",
                        year, month, company.getId());

                throw new InvalidOperationException(
                        String.format("The accounting period %d-%02d is CLOSED. " +
                                        "No transactions are allowed. " +
                                        "Closed on: %s by %s. " +
                                        "Contact your administrator to reopen this period.",
                                year, month,
                                period.getClosedAt() != null ? period.getClosedAt().toString() : "N/A",
                                period.getClosedBy() != null ? period.getClosedBy() : "System")
                );
            }
            log.debug("Period {}-{:02d} is OPEN. Transaction allowed.", year, month);
        } else {
            // Period doesn't exist = assumed OPEN (allows working in future periods)
            log.debug("Period {}-{:02d} not found in database. Assuming OPEN.", year, month);
        }
    }

    /**
     * Convenience method using current company from context.
     */
    @Transactional(readOnly = true)
    public void validateDateIsOpen(LocalDate date) {
        Company company = companyContext.getCurrentCompany();
        validateDateIsOpen(date, company);
    }

    // ============================================
    // MANAGEMENT METHODS (Used by Controller)
    // ============================================

    /**
     * Closes an accounting period, preventing further transactions.
     * This is typically done during month-end or year-end closing procedures.
     *
     * @param year The year of the period
     * @param month The month of the period (1-12)
     * @param closedBy Username or identifier of who is closing the period
     * @param notes Optional notes explaining the closure
     * @return The closed period DTO
     */
    @Transactional
    public AccountingPeriodResponseDTO closePeriod(Integer year, Integer month, String closedBy, String notes) {
        Company company = companyContext.getCurrentCompany();

        log.info("Closing period {}-{:02d} for company: {} by user: {}",
                year, month, company.getId(), closedBy);

        // Validate input
        validateYearMonth(year, month);

        // Find or create the period
        AccountingPeriod period = repository.findByCompanyAndYearAndMonth(company, year, month)
                .orElseGet(() -> {
                    log.debug("Period {}-{:02d} does not exist. Creating new period to close.", year, month);
                    AccountingPeriod newPeriod = new AccountingPeriod();
                    newPeriod.setCompany(company);
                    newPeriod.setYear(year);
                    newPeriod.setMonth(month);
                    return newPeriod;
                });

        // Check if already closed
        if (!period.isOpen()) {
            log.warn("Period {}-{:02d} is already closed", year, month);
            throw new InvalidOperationException(
                    String.format("Period %d-%02d is already closed on %s by %s",
                            year, month,
                            period.getClosedAt() != null ? period.getClosedAt().toString() : "N/A",
                            period.getClosedBy() != null ? period.getClosedBy() : "Unknown")
            );
        }

        // Close the period
        period.setOpen(false);
        period.setClosedAt(LocalDateTime.now());
        period.setClosedBy(closedBy);
        period.setClosingNotes(notes);

        AccountingPeriod saved = repository.save(period);

        log.info("Period {}-{:02d} closed successfully", year, month);

        return mapToResponseDTO(saved);
    }

    /**
     * Reopens a previously closed accounting period.
     * This should be restricted to administrators and used sparingly for error corrections.
     *
     * IMPORTANT: Reopening notes are MANDATORY for audit trail and accountability.
     *
     * @param year The year of the period
     * @param month The month of the period (1-12)
     * @param reopenedBy Username or identifier of who is reopening the period
     * @param notes Mandatory notes explaining why the period is being reopened
     * @return The reopened period DTO
     */
    @Transactional
    public AccountingPeriodResponseDTO reopenPeriod(Integer year, Integer month, String reopenedBy, String notes) {
        Company company = companyContext.getCurrentCompany();

        log.warn("REOPENING period {}-{:02d} for company: {} by user: {}",
                year, month, company.getId(), reopenedBy);

        // Validate input
        validateYearMonth(year, month);

        // Validate notes are provided
        if (notes == null || notes.trim().isEmpty()) {
            throw new InvalidOperationException(
                    "Reopening notes are required for audit trail. You must document why this period is being reopened."
            );
        }

        // Find the period
        AccountingPeriod period = repository.findByCompanyAndYearAndMonth(company, year, month)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Period %d-%02d does not exist and cannot be reopened", year, month)
                ));

        // Check if already open
        if (period.isOpen()) {
            throw new InvalidOperationException(
                    String.format("Period %d-%02d is already open", year, month)
            );
        }

        // Reopen the period
        period.setOpen(true);
        period.setReopenedAt(LocalDateTime.now());
        period.setReopenedBy(reopenedBy);
        period.setReopeningNotes(notes);

        AccountingPeriod saved = repository.save(period);

        log.warn("Period {}-{:02d} REOPENED. Reason: {}", year, month, notes);

        return mapToResponseDTO(saved);
    }

    // ============================================
    // QUERY METHODS
    // ============================================

    /**
     * Gets all accounting periods for the current company, ordered by year and month descending.
     *
     * @return List of all periods (open and closed)
     */
    @Transactional(readOnly = true)
    public List<AccountingPeriodResponseDTO> getAllPeriods() {
        Company company = companyContext.getCurrentCompany();

        log.debug("Retrieving all accounting periods for company: {}", company.getId());

        return repository.findByCompanyOrderByYearDescMonthDesc(company)
                .stream()
                .map(this::mapToResponseDTO)
                .toList();
    }

    /**
     * Gets all closed periods for the current company.
     *
     * @return List of closed periods
     */
    @Transactional(readOnly = true)
    public List<AccountingPeriodResponseDTO> getClosedPeriods() {
        Company company = companyContext.getCurrentCompany();

        log.debug("Retrieving closed accounting periods for company: {}", company.getId());

        return repository.findByCompanyAndIsOpenFalseOrderByYearDescMonthDesc(company)
                .stream()
                .map(this::mapToResponseDTO)
                .toList();
    }

    /**
     * Gets all open periods for the current company.
     *
     * @return List of open periods
     */
    @Transactional(readOnly = true)
    public List<AccountingPeriodResponseDTO> getOpenPeriods() {
        Company company = companyContext.getCurrentCompany();

        log.debug("Retrieving open accounting periods for company: {}", company.getId());

        return repository.findByCompanyAndIsOpenTrueOrderByYearDescMonthDesc(company)
                .stream()
                .map(this::mapToResponseDTO)
                .toList();
    }

    /**
     * Gets a specific accounting period for the current company.
     *
     * @param year The year
     * @param month The month (1-12)
     * @return Optional containing the period if it exists
     */
    @Transactional(readOnly = true)
    public Optional<AccountingPeriodResponseDTO> getPeriod(Integer year, Integer month) {
        Company company = companyContext.getCurrentCompany();

        log.debug("Retrieving period {}-{:02d} for company: {}", year, month, company.getId());

        return repository.findByCompanyAndYearAndMonth(company, year, month)
                .map(this::mapToResponseDTO);
    }

    /**
     * Checks if a specific period exists and is closed.
     *
     * @param year The year
     * @param month The month (1-12)
     * @return true if period exists and is closed, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isPeriodClosed(Integer year, Integer month) {
        Company company = companyContext.getCurrentCompany();

        return repository.findByCompanyAndYearAndMonth(company, year, month)
                .map(period -> !period.isOpen())
                .orElse(false); // Non-existent periods are considered open
    }

    /**
     * Checks if a specific period exists and is open.
     *
     * @param year The year
     * @param month The month (1-12)
     * @return true if period doesn't exist OR exists and is open, false if exists and closed
     */
    @Transactional(readOnly = true)
    public boolean isPeriodOpen(Integer year, Integer month) {
        Company company = companyContext.getCurrentCompany();

        return repository.findByCompanyAndYearAndMonth(company, year, month)
                .map(AccountingPeriod::isOpen)
                .orElse(true); // Non-existent periods are considered open
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    /**
     * Validates year and month are within acceptable ranges.
     */
    private void validateYearMonth(Integer year, Integer month) {
        if (year == null || year < 1900 || year > 2100) {
            throw new InvalidOperationException(
                    String.format("Invalid year: %s. Year must be between 1900 and 2100", year)
            );
        }

        if (month == null || month < 1 || month > 12) {
            throw new InvalidOperationException(
                    String.format("Invalid month: %s. Month must be between 1 and 12", month)
            );
        }
    }

    /**
     * Maps AccountingPeriod entity to response DTO.
     */
    private AccountingPeriodResponseDTO mapToResponseDTO(AccountingPeriod entity) {
        if (entity == null) {
            return null;
        }

        return AccountingPeriodResponseDTO.builder()
                .id(entity.getId())
                .year(entity.getYear())
                .month(entity.getMonth())
                .periodCode(entity.getPeriodCode())
                .isOpen(entity.isOpen())
                .closedAt(entity.getClosedAt())
                .closedBy(entity.getClosedBy())
                .closingNotes(entity.getClosingNotes())
                .reopenedAt(entity.getReopenedAt())
                .reopenedBy(entity.getReopenedBy())
                .reopeningNotes(entity.getReopeningNotes())
                .build();
    }
}