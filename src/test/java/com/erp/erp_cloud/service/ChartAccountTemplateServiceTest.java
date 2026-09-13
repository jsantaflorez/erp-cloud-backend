package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.ChartAccountTemplateEntryDTO;
import com.erp.erp_cloud.entity.ChartAccountTemplateEntry;
import com.erp.erp_cloud.enums.ChartTemplateType;
import com.erp.erp_cloud.repository.ChartAccountTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain unit tests for ChartAccountTemplateService -- no Spring context, no
 * database. This service is deliberately not tenant-scoped (it serves
 * shared PUC reference data), so unlike the other service tests here there
 * is no TenantContext setup involved.
 */
class ChartAccountTemplateServiceTest {

    @Mock private ChartAccountTemplateRepository repository;

    private ChartAccountTemplateService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ChartAccountTemplateService(repository);
    }

    private ChartAccountTemplateEntry entry(String code, String name) {
        ChartAccountTemplateEntry e = new ChartAccountTemplateEntry();
        e.setCode(code);
        e.setName(name);
        return e;
    }

    @Test
    @DisplayName("getEntries maps entities to DTOs in repository order, for the requested template type")
    void getEntries_mapsEntitiesToDtos() {
        when(repository.findByTemplateTypeOrderByCode(ChartTemplateType.COMERCIAL))
                .thenReturn(List.of(
                        entry("11", "Disponible"),
                        entry("1105", "Caja"),
                        entry("110505", "Caja general")
                ));

        List<ChartAccountTemplateEntryDTO> result = service.getEntries(ChartTemplateType.COMERCIAL);

        assertThat(result).containsExactly(
                ChartAccountTemplateEntryDTO.builder().code("11").name("Disponible").build(),
                ChartAccountTemplateEntryDTO.builder().code("1105").name("Caja").build(),
                ChartAccountTemplateEntryDTO.builder().code("110505").name("Caja general").build()
        );
        verify(repository).findByTemplateTypeOrderByCode(ChartTemplateType.COMERCIAL);
    }

    @Test
    @DisplayName("getEntries returns an empty list when the template has no rows, never null")
    void getEntries_withNoRows_returnsEmptyList() {
        when(repository.findByTemplateTypeOrderByCode(ChartTemplateType.SOLIDARIO))
                .thenReturn(List.of());

        List<ChartAccountTemplateEntryDTO> result = service.getEntries(ChartTemplateType.SOLIDARIO);

        assertThat(result).isEmpty();
    }
}
