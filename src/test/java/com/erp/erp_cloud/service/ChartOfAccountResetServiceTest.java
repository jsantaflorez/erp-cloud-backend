package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.ChartOfAccountResetPreviewDTO;
import com.erp.erp_cloud.dto.ChartOfAccountResetRequest;
import com.erp.erp_cloud.dto.ChartOfAccountResetResultDTO;
import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.repository.CompanyRepository;
import com.erp.erp_cloud.security.context.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests (no DB) for ChartOfAccountResetService. Native SQL is faked
 * by matching on the statement text: counts come from {@link #counts},
 * updates/deletes are recorded in {@link #executed}.
 */
class ChartOfAccountResetServiceTest {

    private static final Long COMPANY_ID = 1L;

    private EntityManager entityManager;
    private CompanyRepository companyRepository;
    private ChartOfAccountResetService service;
    private Company company;

    private final Map<String, Long> counts = new HashMap<>();
    private final List<String> executed = new ArrayList<>();
    private List<Object[]> taxes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
        companyRepository = mock(CompanyRepository.class);
        service = new ChartOfAccountResetService(entityManager, companyRepository);

        company = new Company();
        company.setId(COMPANY_ID);
        company.setDefaultGainAccount(new ChartOfAccounts());
        TenantContext.setContext(company);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));

        when(entityManager.createNativeQuery(anyString())).thenAnswer(inv -> fakeQuery(inv.getArgument(0)));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Query fakeQuery(String sql) {
        Query q = mock(Query.class);
        when(q.setParameter(anyInt(), any())).thenReturn(q);
        when(q.getSingleResult()).thenAnswer(inv -> counts.entrySet().stream()
                .filter(e -> sql.contains(e.getKey())).map(Map.Entry::getValue).findFirst().orElse(0L));
        when(q.getResultList()).thenAnswer(inv -> sql.contains("FROM taxes") ? taxes : List.of());
        when(q.executeUpdate()).thenAnswer(inv -> {
            executed.add(sql);
            return sql.startsWith("DELETE FROM chart_of_accounts") ? 632 : sql.startsWith("DELETE FROM taxes") ? taxes.size() : 3;
        });
        return q;
    }

    @Test
    @DisplayName("preview: allowed with no movements, lists what is cleared and the taxes to delete")
    void preview_allowed() {
        counts.put("FROM chart_of_accounts", 632L);
        counts.put("FROM document_types", 2L);
        taxes = new ArrayList<>(List.<Object[]>of(new Object[]{"IVA19", "IVA 19%"}));

        ChartOfAccountResetPreviewDTO p = service.preview();

        assertThat(p.allowed()).isTrue();
        assertThat(p.blockers()).isEmpty();
        assertThat(p.accountCount()).isEqualTo(632);
        assertThat(p.documentTypesWithDefaultAccount()).isEqualTo(2);
        assertThat(p.companyClosingAccountsSet()).isTrue();
        assertThat(p.taxesToDelete()).containsExactly("IVA19 - IVA 19%");
    }

    @Test
    @DisplayName("preview: any journal entry (even annulled) blocks the reset")
    void preview_blockedByEntries() {
        counts.put("FROM journal_entry_items", 1L);

        ChartOfAccountResetPreviewDTO p = service.preview();

        assertThat(p.allowed()).isFalse();
        assertThat(p.blockers()).singleElement().asString().contains("comprobante");
    }

    @Test
    @DisplayName("reset: wrong confirmation word is refused before touching anything")
    void reset_wrongConfirmation() {
        assertThatThrownBy(() -> service.reset(new ChartOfAccountResetRequest("borrar"), "admin"))
                .isInstanceOfSatisfying(InvalidOperationException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("RESET_CONFIRMATION_REQUIRED"));
        assertThat(executed).isEmpty();
        verify(companyRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("reset: blocked by movements -> nothing deleted")
    void reset_blocked() {
        counts.put("FROM journal_entry_items", 5L);
        counts.put("FROM accounting_periods", 1L);

        assertThatThrownBy(() -> service.reset(new ChartOfAccountResetRequest("BORRAR"), "admin"))
                .isInstanceOfSatisfying(InvalidOperationException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo("CHART_OF_ACCOUNTS_RESET_BLOCKED");
                    assertThat((List<?>) ex.getDetails()).hasSize(2);
                });
        assertThat(executed).isEmpty();
        verify(companyRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("reset: clears company closing accounts, document-type defaults and taxes, then deletes the plan")
    void reset_success() {
        taxes = new ArrayList<>(List.<Object[]>of(new Object[]{"IVA19", "IVA 19%"}));

        ChartOfAccountResetResultDTO r = service.reset(new ChartOfAccountResetRequest("BORRAR"), "admin");

        assertThat(company.getDefaultGainAccount()).isNull();
        assertThat(company.getDefaultLossAccount()).isNull();
        assertThat(company.getChartTemplate()).isNull();
        verify(companyRepository).saveAndFlush(company);
        assertThat(executed).hasSize(4);
        assertThat(executed.get(0)).startsWith("DELETE FROM taxes");
        assertThat(executed.get(1)).startsWith("UPDATE document_types");
        assertThat(executed.get(2)).startsWith("UPDATE chart_of_accounts SET parent_id = NULL");
        assertThat(executed.get(3)).startsWith("DELETE FROM chart_of_accounts");
        assertThat(r.accountsDeleted()).isEqualTo(632);
        assertThat(r.taxesDeleted()).isEqualTo(1);
        assertThat(r.companyClosingAccountsCleared()).isTrue();
        verify(entityManager).clear();
    }
}
