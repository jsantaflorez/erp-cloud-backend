package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.DocumentTypeRequest;
import com.erp.erp_cloud.entity.DocumentType;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.repository.DocumentTypeRepository;
import com.erp.erp_cloud.security.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentTypeService {

    private final DocumentTypeRepository repository;
    private final CompanyContext companyContext;

    @Transactional(readOnly = true)
    public List<DocumentType> listAll() {
        // Filter by the active company context
        return repository.findByCompanyIdAndActiveTrue(companyContext.getCurrentCompany().getId());
    }

    @Transactional(readOnly = true)
    public DocumentType findById(Long id) {
        // Find and validate that it belongs to the current company (Multi-tenant security)
        return repository.findById(id)
                .filter(dt -> dt.getCompany().getId().equals(companyContext.getCurrentCompany().getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Document Type with ID " + id + " not found for your company"));
    }

    @Transactional
    public DocumentType create(DocumentType documentType) {
        Company company = companyContext.getCurrentCompany();

        if (repository.existsByCompanyAndCode(company, documentType.getCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Document code '" + documentType.getCode() + "' already exists for this company.");
        }

        documentType.setCompany(company);
        if (documentType.getCurrentConsecutive() == null) {
            documentType.setCurrentConsecutive(0L);
        }

        return repository.save(documentType);
    }



    /**
     * Increments the consecutive and returns the next number.
     * Note: In a high-traffic production environment, we would add
     * a Pessimistic Lock here to prevent duplicate numbers.
     */
    @Transactional
    public Long getNextConsecutive(Long id) {
        DocumentType dt = findById(id);
        Long next = dt.getCurrentConsecutive() + 1;
        dt.setCurrentConsecutive(next);
        repository.save(dt);
        return next;
    }

    @Transactional
    public void deactivate(Long id) {
        DocumentType entity = findById(id);
        entity.setActive(false);
        repository.save(entity);
    }

    @Transactional
    public void resetConsecutive(Long id, Long newConsecutive) {
        DocumentType existing = findById(id);

        if (newConsecutive < existing.getCurrentConsecutive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot assign a consecutive lower than the current one (" + existing.getCurrentConsecutive() + ").");
        }

        existing.setCurrentConsecutive(newConsecutive);
        repository.save(existing);
    }

    // =====================================================
    // SEARCH
    // =====================================================
    @Transactional(readOnly = true)
    public List<DocumentType> searchByName(String name) {
        Company company = companyContext.getCurrentCompany();
        return repository.findByCompanyAndNameContainingIgnoreCase(company, name);
    }


    // =====================================================
    // UPDATE
    // =====================================================
    @Transactional
    public DocumentType update(Long id, DocumentTypeRequest request) {
        // 1. Validate existence and company ownership
        DocumentType existing = findById(id);
        Company company = companyContext.getCurrentCompany();

        // 2. If the code is changing, check if the new code is already taken
        if (!existing.getCode().equals(request.getCode())) {
            if (repository.existsByCompanyAndCode(company, request.getCode())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "The new code '" + request.getCode() + "' is already in use.");
            }
        }

        // 3. Update fields
        existing.setCode(request.getCode());
        existing.setName(request.getName());
        existing.setPrefix(request.getPrefix());
        existing.setAccounting(request.getIsAccounting());
        existing.setLegalResolution(request.getLegalResolution());



        return repository.save(existing);
    }






}