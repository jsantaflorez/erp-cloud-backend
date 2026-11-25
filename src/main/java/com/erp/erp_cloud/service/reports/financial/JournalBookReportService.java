package com.erp.erp_cloud.service.reports.financial;

import com.erp.erp_cloud.dto.reports.financial.JournalBookEntry;
import com.erp.erp_cloud.dto.reports.financial.JournalBookHeaderRow;
import com.erp.erp_cloud.dto.reports.financial.JournalBookLineRow;
import com.erp.erp_cloud.dto.reports.financial.JournalBookPage;
import com.erp.erp_cloud.dto.reports.financial.JournalBookTotals;
import com.erp.erp_cloud.entity.ThirdParty;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.repository.JournalEntryRepository;
import com.erp.erp_cloud.service.base.TenantAwareService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fase 4 (2026-11-24, auditoría F1-25 / A6): Libro Diario sin límite de
 * tamaño. Reemplaza el "traer todo en una página" (size=10000, cortado por
 * Spring en 2.000) del Libro Diario, el Buscador por valor, el Resumen por
 * tipo de comprobante y el export del listado de documentos.
 *
 * - page(): una página de documentos por cursor (entryDate, id), con sus
 *   renglones. Dos consultas por página, ambas con proyección DTO (sin
 *   hidratar entidades ni N+1), y la conexión se suelta al terminar cada
 *   página: con un pool de 5 conexiones ningún libro largo la acapara.
 * - totals(): totales exactos (SUM en la base, suma en BigDecimal) para los
 *   mismos filtros, separados por el filtro de estado del Libro Diario. El
 *   cliente los muestra y verifica contra lo que recibió página a página.
 *
 * Todo filtra por la compañía del token (TenantContext).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JournalBookReportService extends TenantAwareService {

    /** Documentos por página por defecto y máximo (lo que admite max-page-size). */
    public static final int MAX_PAGE_SIZE = 500;

    private final JournalEntryRepository journalEntryRepository;

    /**
     * @param afterDate cursor: fecha del último documento de la página anterior (null en la primera)
     * @param afterId   cursor: id del último documento de la página anterior (null en la primera)
     * @param size      documentos por página, 1..500 (null = 500)
     */
    public JournalBookPage page(LocalDate startDate, LocalDate endDate, Long documentTypeId, String searchTerm,
                                LocalDate afterDate, Long afterId, Integer size) {
        validateRange(startDate, endDate);
        if ((afterDate == null) != (afterId == null)) {
            throw new InvalidOperationException(
                    "afterDate and afterId must be sent together.", "JOURNAL_BOOK_INVALID_CURSOR");
        }
        int pageSize = size == null ? MAX_PAGE_SIZE : Math.max(1, Math.min(MAX_PAGE_SIZE, size));
        Long companyId = currentTenantId();
        String term = normalizeTerm(searchTerm);

        // size + 1: the extra row only says whether there is a next page.
        List<JournalBookHeaderRow> headers = journalEntryRepository.findJournalBookHeaders(
                companyId, term, startDate, endDate, documentTypeId, afterDate, afterId,
                PageRequest.of(0, pageSize + 1));
        boolean hasMore = headers.size() > pageSize;
        if (hasMore) {
            headers = headers.subList(0, pageSize);
        }

        Map<Long, JournalBookEntry> byId = new LinkedHashMap<>();
        for (JournalBookHeaderRow h : headers) {
            byId.put(h.id(), JournalBookEntry.builder()
                    .id(h.id())
                    .documentNumber(h.documentNumber())
                    .consecutive(h.consecutive())
                    .documentTypeId(h.documentTypeId())
                    .entryDate(h.entryDate())
                    .description(h.description())
                    .annulled(Boolean.TRUE.equals(h.annulled()))
                    .annulmentReason(h.annulmentReason())
                    .reversalEntryId(h.reversalEntryId())
                    .reversalDocumentNumber(h.reversalDocumentNumber())
                    .reversalOfEntryId(h.reversalOfEntryId())
                    .reversalOfDocumentNumber(h.reversalOfDocumentNumber())
                    .items(new ArrayList<>())
                    .build());
        }

        if (!byId.isEmpty()) {
            for (JournalBookLineRow l : journalEntryRepository.findJournalBookLines(companyId, byId.keySet())) {
                JournalBookEntry entry = byId.get(l.entryId());
                if (entry == null) continue; // defensive: only lines of this page's documents
                entry.getItems().add(toLine(l));
            }
        }

        JournalBookHeaderRow last = headers.isEmpty() ? null : headers.get(headers.size() - 1);
        return JournalBookPage.builder()
                .entries(new ArrayList<>(byId.values()))
                .hasMore(hasMore)
                .nextAfterDate(hasMore ? last.entryDate() : null)
                .nextAfterId(hasMore ? last.id() : null)
                .build();
    }

    public JournalBookTotals totals(LocalDate startDate, LocalDate endDate, Long documentTypeId, String searchTerm) {
        validateRange(startDate, endDate);
        Long companyId = currentTenantId();
        String term = normalizeTerm(searchTerm);
        JournalBookTotals.Bucket active = bucket(journalEntryRepository.sumJournalBook(
                companyId, term, startDate, endDate, documentTypeId, false));
        JournalBookTotals.Bucket annulled = bucket(journalEntryRepository.sumJournalBook(
                companyId, term, startDate, endDate, documentTypeId, true));
        return JournalBookTotals.builder()
                .active(active)
                .annulled(annulled)
                .all(active.plus(annulled))
                .build();
    }

    private static JournalBookEntry.Line toLine(JournalBookLineRow l) {
        String thirdPartyName = l.thirdPartyDocument() == null ? null
                : ThirdParty.legalDisplayName(l.thirdPartyFirstName(), l.thirdPartyMiddleName(),
                        l.thirdPartyLastName(), l.thirdPartySecondLastName(),
                        l.thirdPartyBusinessName(), l.thirdPartyTradeName());
        return JournalBookEntry.Line.builder()
                .id(l.id())
                .accountCode(l.accountCode())
                .accountName(l.accountName())
                .thirdPartyIdNumber(l.thirdPartyDocument())
                .thirdPartyName(thirdPartyName)
                .costCenterName(l.costCenterName())
                .debit(l.debit())
                .credit(l.credit())
                .description(l.description())
                .build();
    }

    /** One aggregate row [documents, lines, SUM(debit), SUM(credit)]; sums are null when no line matched. */
    static JournalBookTotals.Bucket bucket(List<Object[]> rows) {
        if (rows == null || rows.isEmpty() || rows.get(0) == null) {
            return JournalBookTotals.Bucket.empty();
        }
        Object[] r = rows.get(0);
        return new JournalBookTotals.Bucket(
                toLong(r[0]), toLong(r[1]), toAmount(r[2]), toAmount(r[3]));
    }

    private static long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static BigDecimal toAmount(Object value) {
        BigDecimal amount;
        if (value == null) amount = BigDecimal.ZERO;
        else if (value instanceof BigDecimal bd) amount = bd;
        else amount = new BigDecimal(value.toString());
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    /** Blank = no text filter; otherwise as typed, exactly like GET /journal-entries. */
    private static String normalizeTerm(String searchTerm) {
        return (searchTerm == null || searchTerm.isBlank()) ? null : searchTerm;
    }

    private static void validateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new InvalidOperationException(
                    "endDate must not be before startDate.", "JOURNAL_BOOK_INVALID_RANGE");
        }
    }
}
