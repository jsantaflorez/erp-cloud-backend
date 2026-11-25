package com.erp.erp_cloud.service.reports.financial;

import com.erp.erp_cloud.dto.reports.financial.JournalBookEntry;
import com.erp.erp_cloud.dto.reports.financial.JournalBookHeaderRow;
import com.erp.erp_cloud.dto.reports.financial.JournalBookLineRow;
import com.erp.erp_cloud.dto.reports.financial.JournalBookPage;
import com.erp.erp_cloud.dto.reports.financial.JournalBookTotals;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.repository.JournalEntryRepository;
import com.erp.erp_cloud.security.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fase 4 (2026-11-24): Libro Diario paginado por cursor
 * (GET /api/v1/reports/journal-lines) y sus totales exactos.
 */
class JournalBookReportServiceTest {

    private static final Long COMPANY_ID = 7L;
    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 12, 31);

    @Mock
    private JournalEntryRepository repository;

    private JournalBookReportService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TenantContext.setCurrentTenant(COMPANY_ID);
        service = new JournalBookReportService(repository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static JournalBookHeaderRow header(long id, LocalDate date, String number) {
        return new JournalBookHeaderRow(id, date, number, id, 1L, "Doc " + number, false, null, null, null, null, null);
    }

    private static JournalBookLineRow line(long entryId, long id, String debit, String credit) {
        return new JournalBookLineRow(entryId, id, "110505", "Caja", "900123456", null, null, null, null,
                "ACME SAS", "TIENDA", null, new BigDecimal(debit), new BigDecimal(credit), "renglón " + id);
    }

    @Test
    @DisplayName("Primera página: pide size+1 con la compañía del contexto, corta en size y entrega el cursor")
    void firstPage_cutsAtSizeAndReturnsCursor() {
        LocalDate d = LocalDate.of(2026, 3, 1);
        when(repository.findJournalBookHeaders(eq(COMPANY_ID), isNull(), eq(START), eq(END), isNull(),
                isNull(), isNull(), any(Pageable.class)))
                .thenReturn(List.of(header(10, d, "CC-1"), header(11, d, "CC-2"), header(12, d, "CC-3")));
        when(repository.findJournalBookLines(eq(COMPANY_ID), any()))
                .thenReturn(List.of(line(10, 100, "50.00", "0.00"), line(10, 101, "0.00", "50.00"),
                        line(11, 102, "7.25", "0.00")));

        JournalBookPage page = service.page(START, END, null, "  ", null, null, 2);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findJournalBookHeaders(eq(COMPANY_ID), isNull(), eq(START), eq(END), isNull(),
                isNull(), isNull(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(3);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(repository).findJournalBookLines(eq(COMPANY_ID), ids.capture());
        assertThat(new ArrayList<>(ids.getValue())).containsExactly(10L, 11L);

        assertThat(page.isHasMore()).isTrue();
        assertThat(page.getNextAfterDate()).isEqualTo(d);
        assertThat(page.getNextAfterId()).isEqualTo(11L);
        assertThat(page.getEntries()).extracting(JournalBookEntry::getDocumentNumber).containsExactly("CC-1", "CC-2");
        JournalBookEntry first = page.getEntries().get(0);
        assertThat(first.getItems()).hasSize(2);
        assertThat(first.getItems().get(0).getThirdPartyName()).isEqualTo("ACME SAS (TIENDA)");
        assertThat(first.getItems().get(0).getThirdPartyIdNumber()).isEqualTo("900123456");
    }

    @Test
    @DisplayName("Última página: sin más documentos no hay cursor; un documento sin renglones igual aparece")
    void lastPage_noCursor() {
        LocalDate d = LocalDate.of(2026, 4, 2);
        when(repository.findJournalBookHeaders(eq(COMPANY_ID), eq("CC"), isNull(), isNull(), eq(5L),
                eq(d), eq(11L), any(Pageable.class)))
                .thenReturn(List.of(header(20, d, "CC-9")));
        when(repository.findJournalBookLines(eq(COMPANY_ID), any())).thenReturn(List.of());

        JournalBookPage page = service.page(null, null, 5L, "CC", d, 11L, null);

        assertThat(page.isHasMore()).isFalse();
        assertThat(page.getNextAfterDate()).isNull();
        assertThat(page.getNextAfterId()).isNull();
        assertThat(page.getEntries()).hasSize(1);
        assertThat(page.getEntries().get(0).getItems()).isEmpty();
    }

    @Test
    @DisplayName("Página vacía: no consulta renglones")
    void emptyPage_skipsLines() {
        when(repository.findJournalBookHeaders(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of());

        JournalBookPage page = service.page(START, END, null, null, null, null, 500);

        assertThat(page.getEntries()).isEmpty();
        assertThat(page.isHasMore()).isFalse();
        verify(repository, never()).findJournalBookLines(any(), any());
    }

    @Test
    @DisplayName("Tamaño de página: nunca más de 500 (pide 501)")
    void pageSize_isCapped() {
        when(repository.findJournalBookHeaders(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of());
        service.page(START, END, null, null, null, null, 10_000);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findJournalBookHeaders(any(), any(), any(), any(), any(), any(), any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(501);
    }

    @Test
    @DisplayName("Cursor incompleto o rango invertido: se rechaza")
    void invalidCursorOrRange() {
        assertThatThrownBy(() -> service.page(START, END, null, null, START, null, null))
                .isInstanceOf(InvalidOperationException.class);
        assertThatThrownBy(() -> service.page(END, START, null, null, null, null, null))
                .isInstanceOf(InvalidOperationException.class);
        assertThatThrownBy(() -> service.totals(END, START, null, null))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    @DisplayName("Totales: activos y lado anulado (anulados + reversos) por separado; 'todos' = suma exacta")
    void totals_splitByStatusAndAddedInBigDecimal() {
        List<Object[]> active = new ArrayList<>();
        active.add(new Object[]{3L, 8L, new BigDecimal("1000000.10"), new BigDecimal("1000000.10")});
        List<Object[]> annulled = new ArrayList<>();
        annulled.add(new Object[]{2L, 4L, new BigDecimal("0.20"), new BigDecimal("0.20")});
        when(repository.sumJournalBook(eq(COMPANY_ID), isNull(), eq(START), eq(END), isNull(), eq(false))).thenReturn(active);
        when(repository.sumJournalBook(eq(COMPANY_ID), isNull(), eq(START), eq(END), isNull(), eq(true))).thenReturn(annulled);

        JournalBookTotals totals = service.totals(START, END, null, null);

        assertThat(totals.getActive().getEntryCount()).isEqualTo(3);
        assertThat(totals.getAnnulled().getLineCount()).isEqualTo(4);
        assertThat(totals.getAll().getEntryCount()).isEqualTo(5);
        assertThat(totals.getAll().getLineCount()).isEqualTo(12);
        assertThat(totals.getAll().getDebit()).isEqualByComparingTo("1000000.30");
        assertThat(totals.getAll().getCredit()).isEqualByComparingTo("1000000.30");
        verify(repository).sumJournalBook(eq(COMPANY_ID), isNull(), eq(START), eq(END), isNull(), eq(true));
    }

    @Test
    @DisplayName("Totales sin renglones: SUM nulo cuenta como 0,00")
    void totals_nullSumsAreZero() {
        List<Object[]> none = new ArrayList<>();
        none.add(new Object[]{0L, 0L, null, null});
        when(repository.sumJournalBook(any(), any(), any(), any(), any(), anyBoolean())).thenReturn(none);

        JournalBookTotals totals = service.totals(null, null, null, null);

        assertThat(totals.getAll().getEntryCount()).isZero();
        assertThat(totals.getAll().getDebit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(totals.getAll().getDebit().scale()).isEqualTo(2);
    }
}
