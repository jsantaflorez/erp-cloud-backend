package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.TaxRequest;
import com.erp.erp_cloud.dto.TaxResponseDTO;
import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.entity.Tax;
import com.erp.erp_cloud.exception.DuplicateResourceException;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.exception.ResourceNotFoundException;
import com.erp.erp_cloud.repository.ChartOfAccountsRepository;
import com.erp.erp_cloud.repository.TaxRepository;
import com.erp.erp_cloud.service.base.TenantAwareService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class TaxService extends TenantAwareService {

    private static final Logger log = LoggerFactory.getLogger(TaxService.class);

    private final TaxRepository taxRepository;
    private final ChartOfAccountsRepository accountRepository;

    // =====================================================
    // CREATE
    // =====================================================

    public TaxResponseDTO create(TaxRequest request) {
        Long companyId = currentTenantId();

        log.debug("Creating tax with code: {} for company ID: {}", request.getCode(), companyId);

        // ADAPTED: Validate duplicate code using primitive tenant scope
        if (taxRepository.existsByCompanyIdAndCode(companyId, request.getCode())) {
            throw new DuplicateResourceException("Tax", "code", request.getCode());
        }

        Tax tax = new Tax();
        // Set the reference using proxy/ID depending on your structural mapping
        // e.g., if your entity holds Company, or if you use an entity manager reference.
        // For standard entity relations, we map fields via the request mapper helper
        tax.setActive(request.getActive() != null ? request.getActive() : true);

        mapRequestToEntity(request, tax, companyId);

        Tax saved = taxRepository.save(tax);
        log.info("Tax created successfully with id: {} and code: {}", saved.getId(), saved.getCode());

        return mapToResponseDTO(saved);
    }

    // =====================================================
    // READ
    // =====================================================

    @Transactional(readOnly = true)
    public List<TaxResponseDTO> listAll() {
        Long companyId = currentTenantId();

        log.debug("Listing all taxes for company ID: {}", companyId);

        // ADAPTED: Optimized multi-tenant fetch
        return taxRepository.findByCompanyIdOrderByCodeAsc(companyId)
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TaxResponseDTO findById(Long id) {
        return mapToResponseDTO(findEntityById(id));
    }

    /**
     * Internal helper to get the actual entity for internal Service use
     */
    private Tax findEntityById(Long id) {
        Long companyId = currentTenantId();

        log.debug("Finding tax by id: {} for company ID: {}", id, companyId);

        return taxRepository.findById(id)
                .filter(tax -> tax.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Tax", id));
    }

    // =====================================================
    // UPDATE
    // =====================================================

    public TaxResponseDTO update(Long id, TaxRequest request) {
        Long companyId = currentTenantId();
        log.debug("Updating tax id: {} for company ID: {}", id, companyId);

        Tax existing = findEntityById(id);

        // Validate code change (if code changed, check for duplicates)
        if (!existing.getCode().equals(request.getCode())) {
            if (taxRepository.existsByCompanyIdAndCode(companyId, request.getCode())) {
                throw new DuplicateResourceException("Tax", "code", request.getCode());
            }
        }

        mapRequestToEntity(request, existing, companyId);

        Tax updated = taxRepository.save(existing);
        log.info("Tax {} updated successfully", id);

        return mapToResponseDTO(updated);
    }

    // =====================================================
    // ACTIVATE / DEACTIVATE
    // =====================================================

    public void deactivate(Long id) {
        log.debug("Deactivating tax id: {}", id);

        Tax tax = findEntityById(id);
        tax.setActive(false);
        taxRepository.save(tax);

        log.info("Tax {} deactivated successfully", id);
    }

    public void activate(Long id) {
        log.debug("Activating tax id: {}", id);

        Tax tax = findEntityById(id);
        tax.setActive(true);
        taxRepository.save(tax);

        log.info("Tax {} activated successfully", id);
    }

    // =====================================================
    // MAPPING HELPERS
    // =====================================================

    /**
     * Maps request DTO to entity using current tenant isolation rules
     */
    private void mapRequestToEntity(TaxRequest request, Tax entity, Long companyId) {
        entity.setCode(request.getCode());
        entity.setName(request.getName());
        entity.setType(request.getType());
        entity.setRate(request.getRate());
        entity.setRequiresBase(request.isRequiresBase());
        entity.setMinimumBase(request.getMinimumBase());
        entity.setSign(request.getSign());

        // Validate and set ChartOfAccount restricted by primitive tenant ID
        ChartOfAccounts account = accountRepository.findById(request.getAccountId())
                .filter(acc -> acc.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("ChartOfAccount", request.getAccountId()));

        // Business rule: Tax account must be a posting account
        if (!account.isPostingAccount()) {
            throw new InvalidOperationException(
                    String.format("Account %s is not a posting account and cannot be used for taxes.", account.getCode())
            );
        }

        // Business rule: Tax account must be active
        if (!account.isActive()) {
            throw new InvalidOperationException(
                    String.format("Account %s is inactive and cannot be used for taxes.", account.getCode())
            );
        }

        entity.setAccount(account);
    }

    /**
     * Maps entity to response DTO
     */
    private TaxResponseDTO mapToResponseDTO(Tax entity) {
        if (entity == null) {
            return null;
        }

        TaxResponseDTO dto = new TaxResponseDTO();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setType(entity.getType());
        dto.setRate(entity.getRate());
        dto.setRequiresBase(entity.isRequiresBase());
        dto.setMinimumBase(entity.getMinimumBase());
        dto.setSign(entity.getSign());
        dto.setActive(entity.isActive());

        // Account info
        if (entity.getAccount() != null) {
            dto.setAccountId(entity.getAccount().getId());
            dto.setAccountCode(entity.getAccount().getCode());
            dto.setAccountName(entity.getAccount().getName());
        }

        // Audit fields
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        // Formatted display for UI dropdowns
        // e.g., "IVA - 19% (D)"
        dto.setFullDescription(
                String.format("%s - %s%% (%s)",
                        entity.getName(),
                        entity.getRate(),
                        entity.getSign())
        );

        // Full tax description with account info
        // e.g., "IVA 19% (Account 240801)"
        if (entity.getAccount() != null) {
            dto.setFullTaxDescription(
                    String.format("%s %s%% (Account %s)",
                            entity.getName(),
                            entity.getRate(),
                            entity.getAccount().getCode())
            );
        } else {
            // Fallback if account is somehow null
            dto.setFullTaxDescription(
                    String.format("%s %s%%",
                            entity.getName(),
                            entity.getRate())
            );
        }

        return dto;
    }
}