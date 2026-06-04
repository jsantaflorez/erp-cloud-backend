package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.DocumentTypeRequest;
import com.erp.erp_cloud.dto.DocumentTypeResponseDTO;
import com.erp.erp_cloud.entity.DocumentType;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.exception.DuplicateResourceException;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.exception.ResourceNotFoundException;
import com.erp.erp_cloud.repository.ChartOfAccountsRepository;
import com.erp.erp_cloud.repository.DocumentTypeRepository;
import com.erp.erp_cloud.security.context.TenantContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentTypeService {

    private static final Logger log = LoggerFactory.getLogger(DocumentTypeService.class);

    private final DocumentTypeRepository repository;
    private final ChartOfAccountsRepository accountRepository;
    private final TenantContext companyContext;
    @Transactional
    public DocumentTypeResponseDTO create(DocumentTypeRequest request) {
        Company company = companyContext.getCurrentCompany();
        log.debug("Creating document type with code: {} for company: {}", request.getCode(), company.getId());

        if (repository.existsByCompanyAndCode(company, request.getCode())) {
            throw new DuplicateResourceException("DocumentType", "code", request.getCode());
        }

        DocumentType entity = new DocumentType();
        entity.setCompany(company);
        entity.setActive(true);
        entity.setCurrentConsecutive(0L);

        mapRequestToEntity(entity, request, company);

        return mapToResponseDTO(repository.save(entity));
    }
    // =====================================================
    // UPDATE
    // =====================================================

    @Transactional
    public DocumentTypeResponseDTO update(Long id, DocumentTypeRequest request) {
        DocumentType existing = findById(id);
        Company company = companyContext.getCurrentCompany();

        if (!existing.getCode().equals(request.getCode()) &&
                repository.existsByCompanyAndCode(company, request.getCode())) {
            throw new DuplicateResourceException("DocumentType", "code", request.getCode());
        }

        mapRequestToEntity(existing, request, company);
        return mapToResponseDTO(repository.save(existing));
    }


    @Transactional(readOnly = true)
    public List<DocumentTypeResponseDTO> listAll() {
        Company company = companyContext.getCurrentCompany();
        log.debug("Listing all active document types for company: {}", company.getId());

        return repository.findByCompanyIdAndActiveTrue(company.getId())
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Internal helper for other services that need the actual Entity.
     */
    @Transactional(readOnly = true)
    public DocumentType findById(Long id) {
        log.debug("Finding document type by id: {}", id);

        return repository.findById(id)
                .filter(dt -> dt.getCompany().getId().equals(companyContext.getCurrentCompany().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("DocumentType", id));
    }

    /**
     * Returns DTO for the Controller.
     */
    @Transactional(readOnly = true)
    public DocumentTypeResponseDTO findByIdDto(Long id) {
        return mapToResponseDTO(findById(id));
    }


    /**
     * Increments the consecutive and returns the next number.
     * Note: In a high-traffic production environment, we use
     * a Pessimistic Lock to prevent duplicate numbers.
     */
    @Transactional
    public Long getNextConsecutive(Long id) {
        log.debug("Getting next consecutive for document type id: {}", id);

        // 1. Fetch with a Lock - Thread B will wait here until Thread A finishes
        DocumentType dt = repository.findByIdWithLock(id)
                .filter(d -> d.getCompany().getId().equals(companyContext.getCurrentCompany().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("DocumentType", id));

        // 2. Increment
        Long next = dt.getCurrentConsecutive() + 1;
        dt.setCurrentConsecutive(next);

        // 3. Save and release lock (at end of Transaction)
        repository.save(dt);

        log.info("Next consecutive for document type {}: {}", id, next);
        return next;
    }

    @Transactional
    public void deactivate(Long id) {
        log.debug("Deactivating document type id: {}", id);

        DocumentType entity = findById(id);
        entity.setActive(false);
        repository.save(entity);

        log.info("Document type {} deactivated successfully", id);
    }

    @Transactional
    public void activate(Long id) {
        log.debug("Activating document type id: {}", id);

        DocumentType entity = findById(id);
        entity.setActive(true);
        repository.save(entity);

        log.info("Document type {} activated successfully", id);
    }

    @Transactional
    public void resetConsecutive(Long id, Long newConsecutive) {
        log.debug("Resetting consecutive for document type id: {} to: {}", id, newConsecutive);

        DocumentType existing = findById(id);

        if (newConsecutive < existing.getCurrentConsecutive()) {
            throw new InvalidOperationException(
                    String.format("Cannot assign a lower consecutive. Current: %d, Requested: %d",
                            existing.getCurrentConsecutive(), newConsecutive)
            );
        }

        existing.setCurrentConsecutive(newConsecutive);
        repository.save(existing);

        log.info("Consecutive reset successfully for document type {}", id);
    }

    // =====================================================
    // SEARCH
    // =====================================================

    @Transactional(readOnly = true)
    public List<DocumentTypeResponseDTO> searchByName(String name) {
        Company company = companyContext.getCurrentCompany();

        log.debug("Searching document types by name: {} for company: {}", name, company.getId());

        return repository.findByCompanyAndNameContainingIgnoreCase(company, name)
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }


    // --- MAPPING HELPERS ---

    private void updateEntityFromRequest(DocumentType entity, DocumentTypeRequest request) {
        entity.setCode(request.getCode());
        entity.setName(request.getName());
        entity.setPrefix(request.getPrefix());
        entity.setAccounting(request.getIsAccounting());
        entity.setLegalResolution(request.getLegalResolution());
    }

    private void mapRequestToEntity(DocumentType entity, DocumentTypeRequest request, Company company) {
        entity.setCode(request.getCode());
        entity.setName(request.getName());
        entity.setPrefix(request.getPrefix());
        entity.setAccounting(request.getIsAccounting());
        entity.setLegalResolution(request.getLegalResolution());

        // Handle the default account logic
        if (request.getDefaultAccountId() != null) {
            var account = accountRepository.findById(request.getDefaultAccountId())
                    .filter(a -> a.getCompany().getId().equals(company.getId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Account", request.getDefaultAccountId()));
            entity.setDefaultAccount(account);
        } else {
            entity.setDefaultAccount(null);
        }
    }
    private DocumentTypeResponseDTO mapToResponseDTO(DocumentType entity) {
        DocumentTypeResponseDTO dto = new DocumentTypeResponseDTO();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setPrefix(entity.getPrefix());
        dto.setCurrentConsecutive(entity.getCurrentConsecutive());
        dto.setLegalResolution(entity.getLegalResolution());
        dto.setAccounting(entity.isAccounting());
        dto.setActive(entity.isActive());

        if (entity.getDefaultAccount() != null) {
            dto.setDefaultAccountId(entity.getDefaultAccount().getId());
            dto.setDefaultAccountCode(entity.getDefaultAccount().getCode());
            dto.setDefaultAccountName(entity.getDefaultAccount().getName());
        }

        dto.setFullDescription(entity.getCode() + " - " + entity.getName());

        Long next = (entity.getCurrentConsecutive() != null ? entity.getCurrentConsecutive() : 0L) + 1;
        if (entity.getPrefix() != null && !entity.getPrefix().trim().isEmpty()) {
            dto.setNextNumberPreview(entity.getPrefix().trim() + "-" + next);
        } else {
            dto.setNextNumberPreview(next.toString());
        }

        return dto;
    }
}