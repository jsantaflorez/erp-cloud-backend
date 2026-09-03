package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.AccountingPeriodResponseDTO;
import com.erp.erp_cloud.entity.AccountingPeriod;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.exception.ResourceNotFoundException;
import com.erp.erp_cloud.repository.AccountingPeriodRepository;
import com.erp.erp_cloud.security.context.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain unit tests for AccountingPeriodService -- no Spring context, no
 * database. This service has no manual QA checklist section (it postdates
 * checklist-pruebas-erp.md), so coverage here is derived straight from the
 * business logic in the service: period open/close, the year-end blanket
 * lock, and reopening.
 *
 * Runs via `./gradlew test` (no live MySQL needed).
 */
class AccountingPeriodServiceTest {

    private static final Long COMPANY_ID = 1L;

    @Mock private AccountingPeriodRepository repository;
    @Mock private EntityManager entityManager;

    private AccountingPeriodService service;
    private Company testCompany;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TenantContext.setCurrentTenant(COMPANY_ID);

        service = new AccountingPeriodService(repository, entityManager);

        testCompany = new Company();
        testCompany.setId(COMPANY_ID);

        when(entityManager.getReference(Company.class, COMPANY_ID)).thenReturn(testCompany);
        when(repository.save(any(AccountingPeriod.class))).thenAnswer(invocation -> {
            AccountingPeriod p = invocation.getArgument(0);
            if (p.getId() == null) p.setId(100L);
            return p;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private AccountingPeriod existingPeriod(Integer year, Integer month, boolean open) {
        AccountingPeriod p = new AccountingPeriod();
        p.setId(5L);
        p.setYear(year);
        p.setMonth(month);
        p.setOpen(open);
        p.setCompany(testCompany);
        return p;
    }

    // ============================================================
    // closePeriod() / closeYear()
    // ============================================================

    @Test
    @DisplayName("closePeriod() creates a new period record when none exists yet, and closes it")
    void closePeriod_createsNewPeriod_whenNoneExists() {
        when(repository.findByCompanyIdAndYearAndMonth(COMPANY_ID, 2026, 3)).thenReturn(Optional.empty());

        AccountingPeriodResponseDTO result = service.closePeriod(2026, 3, "jaime", "month-end close");

        ArgumentCaptor<AccountingPeriod> captor = ArgumentCaptor.forClass(AccountingPeriod.class);
        verify(repository).save(captor.capture());
        AccountingPeriod saved = captor.getValue();

        assertThat(saved.getCompany()).isEqualTo(testCompany);
        assertThat(saved.getYear()).isEqualTo(2026);
        assertThat(saved.getMonth()).isEqualTo(3);
        assertThat(saved.isOpen()).isFalse();
        assertThat(saved.isYearClose()).isFalse();
        assertThat(saved.getClosedBy()).isEqualTo("jaime");
        assertThat(saved.getClosingNotes()).isEqualTo("month-end close");
        assertThat(saved.getClosedAt()).isNotNull();
        assertThat(result.isOpen()).isFalse();
    }

    @Test
    @DisplayName("closePeriod() closes an already-existing period record instead of duplicating it")
    void closePeriod_updatesExistingPeriod_whenAlreadyExists() {
        AccountingPeriod existing = existingPeriod(2026, 3, true);
        when(repository.findByCompanyIdAndYearAndMonth(COMPANY_ID, 2026, 3)).thenReturn(Optional.of(existing));

        service.closePeriod(2026, 3, "jaime", "close");

        ArgumentCaptor<AccountingPeriod> captor = ArgumentCaptor.forClass(AccountingPeriod.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
        assertThat(existing.isOpen()).isFalse();
    }

    @Test
    @DisplayName("closePeriod() rejects a year outside 1900-2100")
    void closePeriod_invalidYear_throws() {
        assertThatThrownBy(() -> service.closePeriod(1899, 1, "jaime", "x"))
                .isInstanceOf(InvalidOperationException.class);
        assertThatThrownBy(() -> service.closePeriod(2101, 1, "jaime", "x"))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    @DisplayName("closePeriod() rejects a month outside 1-12")
    void closePeriod_invalidMonth_throws() {
        assertThatThrownBy(() -> service.closePeriod(2026, 0, "jaime", "x"))
                .isInstanceOf(InvalidOperationException.class);
        assertThatThrownBy(() -> service.closePeriod(2026, 13, "jaime", "x"))
                .isInstanceOf(InvalidOperationException.class);
    }

    private List<AccountingPeriod> allMonthsClosedExceptDecember(Integer year) {
        List<AccountingPeriod> periods = new java.util.ArrayList<>();
        for (int month = 1; month <= 11; month++) {
            periods.add(existingPeriod(year, month, false)); // closed
        }
        return periods;
    }

    @Test
    @DisplayName("closeYear() seals December's period record with the year-close flag, once months 1-11 are closed")
    void closeYear_setsYearCloseTrue_onDecemberRecord_whenAllOtherMonthsClosed() {
        when(repository.findByCompanyIdAndYear(COMPANY_ID, 2026)).thenReturn(allMonthsClosedExceptDecember(2026));
        when(repository.findByCompanyIdAndYearAndMonth(COMPANY_ID, 2026, 12)).thenReturn(Optional.empty());

        service.closeYear(2026, "jaime", "annual close");

        ArgumentCaptor<AccountingPeriod> captor = ArgumentCaptor.forClass(AccountingPeriod.class);
        verify(repository).save(captor.capture());
        AccountingPeriod saved = captor.getValue();

        assertThat(saved.getMonth()).isEqualTo(12);
        assertThat(saved.isYearClose()).isTrue();
        assertThat(saved.isOpen()).isFalse();
    }

    @Test
    @DisplayName("closeYear() rejects sealing the year while some months are still open or missing a record")
    void closeYear_monthsNotAllClosed_throws() {
        List<AccountingPeriod> periods = allMonthsClosedExceptDecember(2026);
        periods.get(4).setOpen(true); // month 5 (index 4) is still open
        // month 8 has no record at all -- simulate by removing it
        periods.removeIf(p -> p.getMonth() == 8);
        when(repository.findByCompanyIdAndYear(COMPANY_ID, 2026)).thenReturn(periods);

        assertThatThrownBy(() -> service.closeYear(2026, "jaime", "annual close"))
                .isInstanceOf(InvalidOperationException.class)
                .satisfies(ex -> {
                    InvalidOperationException ioe = (InvalidOperationException) ex;
                    assertThat(ioe.getErrorCode()).isEqualTo("MONTHS_NOT_CLOSED_BEFORE_YEAR_END");
                    assertThat(ioe.getMessage()).contains("5").contains("8");
                });

        // Must never reach the actual close/save step.
        verify(repository, org.mockito.Mockito.never()).save(any(AccountingPeriod.class));
    }

    @Test
    @DisplayName("closeYear() rejects sealing the year when no monthly periods exist yet at all")
    void closeYear_noPeriodsExistYet_throws() {
        when(repository.findByCompanyIdAndYear(COMPANY_ID, 2026)).thenReturn(List.of());

        assertThatThrownBy(() -> service.closeYear(2026, "jaime", "annual close"))
                .isInstanceOf(InvalidOperationException.class)
                .satisfies(ex -> assertThat(((InvalidOperationException) ex).getErrorCode())
                        .isEqualTo("MONTHS_NOT_CLOSED_BEFORE_YEAR_END"));
    }

    // ============================================================
    // reopenPeriod() / reopenYear()
    // ============================================================

    @Test
    @DisplayName("reopenPeriod() reopens an existing closed period and records the audit fields")
    void reopenPeriod_reopensExistingPeriod() {
        AccountingPeriod existing = existingPeriod(2026, 3, false);
        when(repository.findByCompanyIdAndYearAndMonth(COMPANY_ID, 2026, 3)).thenReturn(Optional.of(existing));

        AccountingPeriodResponseDTO result = service.reopenPeriod(2026, 3, "jaime", "correction needed");

        assertThat(existing.isOpen()).isTrue();
        assertThat(existing.isYearClose()).isFalse();
        assertThat(existing.getReopenedBy()).isEqualTo("jaime");
        assertThat(existing.getReopeningNotes()).isEqualTo("correction needed");
        assertThat(existing.getReopenedAt()).isNotNull();
        assertThat(result.isOpen()).isTrue();
    }

    @Test
    @DisplayName("reopenPeriod() rejects a period that was never created")
    void reopenPeriod_nonExistentPeriod_throwsResourceNotFound() {
        when(repository.findByCompanyIdAndYearAndMonth(COMPANY_ID, 2026, 3)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reopenPeriod(2026, 3, "jaime", "x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("reopenYear() clears the year-close flag only on periods that had it set")
    void reopenYear_clearsYearCloseOnlyOnFlaggedPeriods() {
        AccountingPeriod december = existingPeriod(2026, 12, false);
        december.setYearClose(true);
        AccountingPeriod june = existingPeriod(2026, 6, false); // closed individually, but not year-sealed
        june.setYearClose(false);

        when(repository.findByCompanyIdAndYear(COMPANY_ID, 2026)).thenReturn(List.of(december, june));

        service.reopenYear(2026, "jaime", "unseal for audit adjustment");

        assertThat(december.isYearClose()).isFalse();
        assertThat(december.getReopenedBy()).isEqualTo("jaime");
        assertThat(december.getReopeningNotes()).contains("unseal for audit adjustment");
        // June was never year-sealed -- reopenYear must not touch its audit fields.
        assertThat(june.isYearClose()).isFalse();
        assertThat(june.getReopenedBy()).isNull();
        // June's individual open/closed state is untouched by a year-level unseal.
        assertThat(june.isOpen()).isFalse();
    }

    // ============================================================
    // validateDateIsOpen() -- called by JournalEntryService before posting
    // ============================================================

    @Test
    @DisplayName("validateDateIsOpen() rejects a null date")
    void validateDateIsOpen_nullDate_throws() {
        assertThatThrownBy(() -> service.validateDateIsOpen(null, COMPANY_ID))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    @DisplayName("validateDateIsOpen() rejects any date in a year-closed fiscal year, even if that month's own record is open")
    void validateDateIsOpen_yearClosed_blanketBlocksEveryMonth() {
        when(repository.existsByCompanyIdAndYearAndYearCloseTrue(COMPANY_ID, 2026)).thenReturn(true);

        assertThatThrownBy(() -> service.validateDateIsOpen(LocalDate.of(2026, 3, 15), COMPANY_ID))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("2026")
                .hasMessageContaining("CLOSED");

        // The blanket year lock short-circuits -- the per-month lookup should
        // not even be needed to reach the correct (rejecting) outcome.
    }

    @Test
    @DisplayName("validateDateIsOpen() rejects a date in an individually-closed month")
    void validateDateIsOpen_monthClosed_throws() {
        when(repository.existsByCompanyIdAndYearAndYearCloseTrue(COMPANY_ID, 2026)).thenReturn(false);
        when(repository.findByCompanyIdAndYearAndMonth(COMPANY_ID, 2026, 3))
                .thenReturn(Optional.of(existingPeriod(2026, 3, false)));

        assertThatThrownBy(() -> service.validateDateIsOpen(LocalDate.of(2026, 3, 15), COMPANY_ID))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("CLOSED");
    }

    @Test
    @DisplayName("validateDateIsOpen() allows a date in an open month")
    void validateDateIsOpen_monthOpen_succeeds() {
        when(repository.existsByCompanyIdAndYearAndYearCloseTrue(COMPANY_ID, 2026)).thenReturn(false);
        when(repository.findByCompanyIdAndYearAndMonth(COMPANY_ID, 2026, 3))
                .thenReturn(Optional.of(existingPeriod(2026, 3, true)));

        service.validateDateIsOpen(LocalDate.of(2026, 3, 15), COMPANY_ID); // no exception
    }

    @Test
    @DisplayName("validateDateIsOpen() allows a date in a month with no period record yet (defaults to open)")
    void validateDateIsOpen_noPeriodRecord_defaultsToOpen() {
        when(repository.existsByCompanyIdAndYearAndYearCloseTrue(COMPANY_ID, 2026)).thenReturn(false);
        when(repository.findByCompanyIdAndYearAndMonth(COMPANY_ID, 2026, 3)).thenReturn(Optional.empty());

        service.validateDateIsOpen(LocalDate.of(2026, 3, 15), COMPANY_ID); // no exception
    }

    // ============================================================
    // isPeriodClosed()
    // ============================================================

    @Test
    @DisplayName("isPeriodClosed() returns true when the fiscal year is sealed, regardless of the month record")
    void isPeriodClosed_yearClosed_returnsTrue() {
        when(repository.existsByCompanyIdAndYearAndYearCloseTrue(COMPANY_ID, 2026)).thenReturn(true);

        assertThat(service.isPeriodClosed(2026, 3)).isTrue();
    }

    @Test
    @DisplayName("isPeriodClosed() returns true when the individual month is closed")
    void isPeriodClosed_monthClosedIndividually_returnsTrue() {
        when(repository.existsByCompanyIdAndYearAndYearCloseTrue(COMPANY_ID, 2026)).thenReturn(false);
        when(repository.findByCompanyIdAndYearAndMonth(COMPANY_ID, 2026, 3))
                .thenReturn(Optional.of(existingPeriod(2026, 3, false)));

        assertThat(service.isPeriodClosed(2026, 3)).isTrue();
    }

    @Test
    @DisplayName("isPeriodClosed() returns false for an open month")
    void isPeriodClosed_monthOpen_returnsFalse() {
        when(repository.existsByCompanyIdAndYearAndYearCloseTrue(COMPANY_ID, 2026)).thenReturn(false);
        when(repository.findByCompanyIdAndYearAndMonth(COMPANY_ID, 2026, 3))
                .thenReturn(Optional.of(existingPeriod(2026, 3, true)));

        assertThat(service.isPeriodClosed(2026, 3)).isFalse();
    }

    @Test
    @DisplayName("isPeriodClosed() returns false when no period record exists at all")
    void isPeriodClosed_noRecord_returnsFalse() {
        when(repository.existsByCompanyIdAndYearAndYearCloseTrue(COMPANY_ID, 2026)).thenReturn(false);
        when(repository.findByCompanyIdAndYearAndMonth(COMPANY_ID, 2026, 3)).thenReturn(Optional.empty());

        assertThat(service.isPeriodClosed(2026, 3)).isFalse();
    }
}
