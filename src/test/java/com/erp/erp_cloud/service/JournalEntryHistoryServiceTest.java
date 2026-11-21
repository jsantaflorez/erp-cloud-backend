package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.JournalEntryHistoryDTO;
import com.erp.erp_cloud.dto.JournalEntryResponseDTO;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.JournalEntry;
import com.erp.erp_cloud.entity.JournalEntryHistory;
import com.erp.erp_cloud.repository.JournalEntryHistoryRepository;
import com.erp.erp_cloud.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bloque A (2026-11-21): version history of journal entries.
 */
class JournalEntryHistoryServiceTest {

    private final JournalEntryHistoryRepository repository = mock(JournalEntryHistoryRepository.class);
    private final JournalEntryHistoryService service =
            new JournalEntryHistoryService(repository, new ObjectMapper().findAndRegisterModules());

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private JournalEntry entry() {
        Company company = new Company();
        company.setId(4L);
        JournalEntry entry = new JournalEntry();
        entry.setId(77L);
        entry.setCompany(company);
        entry.setDocumentNumber("CE-120");
        entry.setVersion(2L);
        return entry;
    }

    private JournalEntryResponseDTO before() {
        JournalEntryResponseDTO dto = new JournalEntryResponseDTO();
        dto.setId(77L);
        dto.setDocumentNumber("CE-120");
        dto.setEntryDate(LocalDate.of(2026, 3, 15));
        dto.setDescription("Pago arriendo marzo");
        JournalEntryResponseDTO.ItemResponse line = new JournalEntryResponseDTO.ItemResponse();
        line.setAccountCode("512010");
        line.setDebit(new BigDecimal("8000000.00"));
        line.setCredit(BigDecimal.ZERO);
        dto.setItems(List.of(line));
        return dto;
    }

    @Test
    @DisplayName("record() saves the full previous version with who, when and the entry version")
    void recordSavesSnapshot() {
        UserPrincipal principal = new UserPrincipal(7L, "contador@firma.com", "x", "Contador", 4L, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        service.record(entry(), before(), JournalEntryHistory.ACTION_UPDATE);

        ArgumentCaptor<JournalEntryHistory> captor = ArgumentCaptor.forClass(JournalEntryHistory.class);
        verify(repository).save(captor.capture());
        JournalEntryHistory row = captor.getValue();
        assertThat(row.getCompanyId()).isEqualTo(4L);
        assertThat(row.getJournalEntryId()).isEqualTo(77L);
        assertThat(row.getEntryVersion()).isEqualTo(2L);
        assertThat(row.getAction()).isEqualTo("UPDATE");
        assertThat(row.getChangedByUserId()).isEqualTo(7L);
        assertThat(row.getChangedBy()).isEqualTo("contador@firma.com");
        assertThat(row.getChangedAt()).isNotNull();
        assertThat(row.getSnapshot()).contains("Pago arriendo marzo").contains("512010").contains("8000000");
    }

    @Test
    @DisplayName("record() without an authenticated user is attributed to 'system'")
    void recordWithoutUser() {
        service.record(entry(), before(), JournalEntryHistory.ACTION_ANNUL);

        ArgumentCaptor<JournalEntryHistory> captor = ArgumentCaptor.forClass(JournalEntryHistory.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getChangedBy()).isEqualTo("system");
        assertThat(captor.getValue().getChangedByUserId()).isNull();
    }

    @Test
    @DisplayName("list() returns each version with its snapshot readable as JSON")
    void listReadsSnapshots() {
        JournalEntryHistory row = new JournalEntryHistory();
        row.setId(1L);
        row.setCompanyId(4L);
        row.setJournalEntryId(77L);
        row.setEntryVersion(2L);
        row.setAction("UPDATE");
        row.setChangedBy("contador@firma.com");
        row.setChangedAt(LocalDateTime.of(2026, 11, 21, 10, 0));
        row.setSnapshot("{\"description\":\"Pago arriendo marzo\",\"items\":[{\"accountCode\":\"512010\"}]}");
        when(repository.findByCompanyIdAndJournalEntryIdOrderByIdDesc(4L, 77L)).thenReturn(List.of(row));

        List<JournalEntryHistoryDTO> result = service.list(4L, 77L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).snapshot().get("description").asText()).isEqualTo("Pago arriendo marzo");
        assertThat(result.get(0).snapshot().get("items").get(0).get("accountCode").asText()).isEqualTo("512010");
    }
}
