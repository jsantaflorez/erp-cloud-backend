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
        return repository.findByCompany(companyContext.getCurrentCompany());
    }

    @Transactional
    public DocumentType create(DocumentType documentType) {
        Company company = companyContext.getCurrentCompany();

        // 1. Validation: Ensure code uniqueness within the same company
        if (repository.existsByCompanyAndCode(company, documentType.getCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Document code '" + documentType.getCode() + "' already exists for this company.");
        }

        // 2. Set the owner company
        documentType.setCompany(company);

        // 3. Ensure the consecutive starts at 0 if not specified (Entity also has default)
        if (documentType.getCurrentConsecutive() == null) {
            documentType.setCurrentConsecutive(0L);
        }

        return repository.save(documentType);
    }

    @Transactional(readOnly = true)
    public DocumentType findById(Long id) {
        // Find and validate that it belongs to the current company (Multi-tenant security)
        return repository.findById(id)
                .filter(dt -> dt.getCompany().getId().equals(companyContext.getCurrentCompany().getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Document Type with ID " + id + " not found for your company"));
    }

    /**
     * Increments the consecutive and returns the next number.
     * This will be called by the Accounting Movement logic later.
     */
    @Transactional
    public Long getNextConsecutive(Long id) {
        DocumentType dt = findById(id);

        // Logical increment
        Long next = dt.getCurrentConsecutive() + 1;
        dt.setCurrentConsecutive(next);

        repository.save(dt);
        return next;
    }

    @Transactional
    public void delete(Long id) {
        DocumentType entity = findById(id);

        // TODO: Check if there are already accounting movements using this type [cite: 2026-01-17]
        // if (movementRepository.existsByDocumentType(entity)) { ... }

        repository.delete(entity);
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

    @Transactional
    public void resetConsecutive(Long id, Long newConsecutive) {
        // Ownership and existence check through existing multi-tenant logic
        DocumentType existing = findById(id);

        // Integrity check: Preventing duplicate numbering or legal gaps by going backwards
        if (newConsecutive < existing.getCurrentConsecutive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No se puede asignar un consecutivo menor al actual (" + existing.getCurrentConsecutive() + ").");
        }

        // Update the sequence pointer
        existing.setCurrentConsecutive(newConsecutive);
        repository.save(existing);

        // TODO: Implement Audit Log entry for this critical administrative action
    }

    // =====================================================
    // SEARCH
    // =====================================================
    @Transactional(readOnly = true)
    public List<DocumentType> searchByName(String name) {
        Company company = companyContext.getCurrentCompany();
        return repository.findByCompanyAndNameContainingIgnoreCase(company, name);
    }


}