package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.ChartOfAccountResponseDTO;
import com.erp.erp_cloud.dto.ThirdPartyResponseDTO;
import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.entity.City;
import com.erp.erp_cloud.entity.ThirdParty;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.repository.ChartOfAccountsRepository;
import com.erp.erp_cloud.repository.CityRepository;
import com.erp.erp_cloud.repository.CompanyRepository;
import com.erp.erp_cloud.repository.CostCenterRepository;
import com.erp.erp_cloud.repository.JournalEntryRepository;
import com.erp.erp_cloud.repository.ThirdPartyRepository;
import com.erp.erp_cloud.security.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fase 4 (2026-11-24): lookups de los selectores de cuenta y tercero
 * (GET /chart-of-accounts/lookup y /third-parties/lookup): siempre con la
 * compañía del contexto, texto en minúsculas y escapado para LIKE, tope de
 * 50 resultados y resolución por ids.
 */
class CatalogLookupServiceTest {

    private static final Long COMPANY_ID = 7L;

    @Mock private ChartOfAccountsRepository accountRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private ThirdPartyRepository thirdPartyRepository;
    @Mock private CityRepository cityRepository;
    @Mock private CostCenterRepository costCenterRepository;
    @Mock private JournalEntryRepository journalEntryRepository;

    private ChartOfAccountService accountService;
    private ThirdPartyService thirdPartyService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TenantContext.setCurrentTenant(COMPANY_ID);
        accountService = new ChartOfAccountService(accountRepository, companyRepository);
        thirdPartyService = new ThirdPartyService(thirdPartyRepository, cityRepository,
                costCenterRepository, journalEntryRepository, companyRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static ChartOfAccounts account(long id, String code, String name) {
        ChartOfAccounts a = new ChartOfAccounts();
        a.setId(id);
        a.setCode(code);
        a.setName(name);
        a.setPostingAccount(true);
        a.setActive(true);
        return a;
    }

    private static ThirdParty thirdParty(long id, String doc, String businessName, boolean active) {
        City city = new City();
        city.setId(1L);
        city.setName("Bogota");
        ThirdParty tp = new ThirdParty();
        tp.setId(id);
        tp.setDocumentNumber(doc);
        tp.setBusinessName(businessName);
        tp.setActive(active);
        tp.setCity(city);
        return tp;
    }

    @Test
    @DisplayName("LookupText: minúsculas, recorta y escapa los comodines de LIKE con '!'")
    void lookupText_escapesWildcards() {
        assertThat(LookupText.normalize("  Caja_General ")).isEqualTo("caja_general");
        assertThat(LookupText.prefix("51%")).isEqualTo("51!%%");
        assertThat(LookupText.contains("a_b!")).isEqualTo("%a!_b!!%");
        assertThat(LookupText.clampLimit(null)).isEqualTo(50);
        assertThat(LookupText.clampLimit(500)).isEqualTo(50);
        assertThat(LookupText.clampLimit(0)).isEqualTo(1);
    }

    @Test
    @DisplayName("Cuentas: búsqueda por texto con la compañía del contexto, patrones y tope de 50")
    void accountLookup_textSearch() {
        when(accountRepository.lookup(eq(COMPANY_ID), anyString(), anyString(), anyString(),
                anyBoolean(), anyBoolean(), any(Pageable.class)))
                .thenReturn(List.of(account(1L, "5105", "Gastos de personal")));

        List<ChartOfAccountResponseDTO> result = accountService.lookup(" 51 ", null, null, true, true, 999);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(accountRepository).lookup(eq(COMPANY_ID), eq("51"), eq("51%"), eq("%51%"),
                eq(true), eq(true), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(result).extracting(ChartOfAccountResponseDTO::getCode).containsExactly("5105");
    }

    @Test
    @DisplayName("Cuentas: modo ids resuelve solo las de la compañía y no hace búsqueda por texto")
    void accountLookup_byIds() {
        when(accountRepository.findByCompanyAndIdIn(eq(COMPANY_ID), any()))
                .thenReturn(List.of(account(3L, "110505", "Caja general")));

        List<ChartOfAccountResponseDTO> result = accountService.lookup("ignorado", List.of(3L), null, true, true, null);

        assertThat(result).extracting(ChartOfAccountResponseDTO::getId).containsExactly(3L);
        verify(accountRepository, never()).lookup(any(), any(), any(), any(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    @DisplayName("Cuentas: más de 200 ids se rechaza")
    void accountLookup_tooManyIds() {
        List<Long> ids = LongStream.rangeClosed(1, 201).boxed().toList();
        assertThatThrownBy(() -> accountService.lookup(null, ids, null, true, true, null))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    @DisplayName("Cuentas: prefixOf trae los padres posibles del código, con la compañía del contexto")
    void accountLookup_prefixOf() {
        when(accountRepository.findCodePrefixesOf(COMPANY_ID, "510510"))
                .thenReturn(List.of(account(1L, "5", "Gastos"), account(2L, "51", "Operacionales"), account(3L, "5105", "Personal")));

        List<ChartOfAccountResponseDTO> result = accountService.lookup(null, null, " 510510 ", true, true, null);

        assertThat(result).extracting(ChartOfAccountResponseDTO::getCode).containsExactly("5", "51", "5105");
        assertThat(accountService.lookup(null, null, "  ", true, true, null)).isEmpty();
    }

    @Test
    @DisplayName("Terceros: búsqueda con activeOnly y la compañía del contexto; ids incluye inactivos")
    void thirdPartyLookup() {
        when(thirdPartyRepository.lookup(eq(COMPANY_ID), anyString(), anyString(), anyString(), anyBoolean(), any(Pageable.class)))
                .thenReturn(List.of(thirdParty(10L, "900123456", "ACME SAS", true)));
        when(thirdPartyRepository.findByCompanyAndIdIn(eq(COMPANY_ID), any()))
                .thenReturn(List.of(thirdParty(11L, "800999888", "VIEJO LTDA", false)));

        List<ThirdPartyResponseDTO> found = thirdPartyService.lookup("Acme", null, true, null);
        verify(thirdPartyRepository).lookup(eq(COMPANY_ID), eq("acme"), eq("acme%"), eq("%acme%"), eq(true), any(Pageable.class));
        assertThat(found).extracting(ThirdPartyResponseDTO::getDocumentNumber).containsExactly("900123456");

        List<ThirdPartyResponseDTO> byIds = thirdPartyService.lookup(null, List.of(11L), true, null);
        assertThat(byIds).extracting(ThirdPartyResponseDTO::isActive).containsExactly(false);
    }
}
