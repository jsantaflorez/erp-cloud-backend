package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.ChartAccountTemplateEntryDTO;
import com.erp.erp_cloud.enums.ChartTemplateType;
import com.erp.erp_cloud.repository.ChartAccountTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Reads the reference chart-of-accounts templates (PUC Comercial, PUC
 * Solidario, ...) used to suggest an account name when creating an entry in
 * the Plan de Cuentas. Deliberately NOT tenant-aware -- this is shared
 * lookup data, not per-company data (see ChartAccountTemplateEntry).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChartAccountTemplateService {

    private final ChartAccountTemplateRepository repository;

    /**
     * All entries for one template, ordered by code so a client that wants
     * to render them as-is (rather than only doing prefix lookups) gets a
     * sensible order for free.
     */
    public List<ChartAccountTemplateEntryDTO> getEntries(ChartTemplateType templateType) {
        return repository.findByTemplateTypeOrderByCode(templateType).stream()
                .map(entry -> ChartAccountTemplateEntryDTO.builder()
                        .code(entry.getCode())
                        .name(entry.getName())
                        .build())
                .collect(Collectors.toList());
    }
}
