package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.DocumentTypeRequest;
import com.erp.erp_cloud.dto.DocumentTypeResponseDTO;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentTypeService {

    private final DocumentTypeRepository repository;
    private final CompanyContext companyContext;




    @Transactional(readOnly = true)
    public List<DocumentTypeResponseDTO> listAll() {
        return repository.findByCompanyIdAndActiveTrue(companyContext.getCurrentCompany().getId())
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }


    /**
     * Internal helper for other services that need the actual Entity.
     */
    @Transactional(readOnly = true)
    public DocumentType findById(Long id) {
        return repository.findById(id)
                .filter(dt -> dt.getCompany().getId().equals(companyContext.getCurrentCompany().getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Document Type with ID " + id + " not found for your company"));
    }

    /**
     * Returns DTO for the Controller.
     */
    @Transactional(readOnly = true)
    public DocumentTypeResponseDTO findByIdDto(Long id) {
        return mapToResponseDTO(findById(id));
    }


    @Transactional
    public DocumentTypeResponseDTO create(DocumentTypeRequest request) {
        Company company = companyContext.getCurrentCompany();

        if (repository.existsByCompanyAndCode(company, request.getCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Document code '" + request.getCode() + "' already exists.");
        }

        DocumentType entity = new DocumentType();
        entity.setCompany(company);
        entity.setActive(true);
        // Map fields from Request DTO
        updateEntityFromRequest(entity, request);

        if (entity.getCurrentConsecutive() == null) {
            entity.setCurrentConsecutive(0L);
        }

        return mapToResponseDTO(repository.save(entity));
    }



    /**
     * Increments the consecutive and returns the next number.
     * Note: In a high-traffic production environment, we add
     * a Pessimistic Lock to prevent duplicate numbers.
     */



    @Transactional
    public Long getNextConsecutive(Long id) {
        // 1. Fetch with a Lock - Thread B will wait here until Thread A finishes
        DocumentType dt = repository.findByIdWithLock(id)
                .filter(d -> d.getCompany().getId().equals(companyContext.getCurrentCompany().getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document Type not found"));
        // 2. Increment
        Long next = dt.getCurrentConsecutive() + 1;
        dt.setCurrentConsecutive(next);
        // 3. Save and release lock (at end of Transaction)
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
    public void activate(Long id) {
        DocumentType entity = findById(id);
        entity.setActive(true);
        repository.save(entity);
    }


    @Transactional
    public void resetConsecutive(Long id, Long newConsecutive) {
        DocumentType existing = findById(id);
        if (newConsecutive < existing.getCurrentConsecutive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot assign a lower consecutive.");
        }
        existing.setCurrentConsecutive(newConsecutive);
        repository.save(existing);
    }


    // =====================================================
    // SEARCH
    // =====================================================



    @Transactional(readOnly = true)
    public List<DocumentTypeResponseDTO> searchByName(String name) {
        Company company = companyContext.getCurrentCompany();
        return repository.findByCompanyAndNameContainingIgnoreCase(company, name)
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    // =====================================================
    // UPDATE
    // =====================================================
    @Transactional
    public DocumentTypeResponseDTO update(Long id, DocumentTypeRequest request) {
        DocumentType existing = findById(id);

        if (!existing.getCode().equals(request.getCode())) {
            if (repository.existsByCompanyAndCode(companyContext.getCurrentCompany(), request.getCode())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Code already in use.");
            }
        }

        updateEntityFromRequest(existing, request);
        return mapToResponseDTO(repository.save(existing));
    }
    // --- MAPPING HELPERS ---

    private void updateEntityFromRequest(DocumentType entity, DocumentTypeRequest request) {
        entity.setCode(request.getCode());
        entity.setName(request.getName());
        entity.setPrefix(request.getPrefix());
        entity.setAccounting(request.getIsAccounting());
        entity.setLegalResolution(request.getLegalResolution());
    }

    private DocumentTypeResponseDTO mapToResponseDTO(DocumentType entity) {
        DocumentTypeResponseDTO dto = new DocumentTypeResponseDTO();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setPrefix(entity.getPrefix());
        dto.setCurrentConsecutive(entity.getCurrentConsecutive());
        dto.setAccounting(entity.isAccounting());
        dto.setActive(entity.isActive());

        // Full Description for dropdowns
        dto.setFullDescription(entity.getCode() + " - " + entity.getName());

        // Hybrid Prefix Logic for Preview
        Long next = (entity.getCurrentConsecutive() != null ? entity.getCurrentConsecutive() : 0L) + 1;
        if (entity.getPrefix() != null && !entity.getPrefix().trim().isEmpty()) {
            dto.setNextNumberPreview(entity.getPrefix().trim() + "-" + next);
        } else {
            dto.setNextNumberPreview(next.toString());
        }

        return dto;
    }
}





