package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.ThirdPartyRequest;
import com.erp.erp_cloud.dto.ThirdPartyResponseDTO;
import com.erp.erp_cloud.entity.City;
import com.erp.erp_cloud.entity.CostCenter;
import com.erp.erp_cloud.entity.ThirdParty;
import com.erp.erp_cloud.exception.DuplicateResourceException;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.exception.ResourceNotFoundException;
import com.erp.erp_cloud.repository.CostCenterRepository;
import com.erp.erp_cloud.repository.JournalEntryRepository;
import com.erp.erp_cloud.repository.ThirdPartyRepository;
import com.erp.erp_cloud.repository.CityRepository;
import com.erp.erp_cloud.service.base.TenantAwareService;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class ThirdPartyService extends TenantAwareService {

    private static final Logger log = LoggerFactory.getLogger(ThirdPartyService.class);

    private final ThirdPartyRepository thirdPartyRepository;
    private final CityRepository cityRepository;
    private final CostCenterRepository costCenterRepository;
    private final JournalEntryRepository journalEntryRepository;

    // =====================================================
    // CREATE
    // =====================================================

    public ThirdPartyResponseDTO create(ThirdPartyRequest request) {
        Long companyId = currentTenantId();

        log.debug("Creating third party with document number: {} for company ID: {}",
                request.getDocumentNumber(), companyId);

        // ADAPTED: Validate duplicate document number within primitive tenant scope
        if (thirdPartyRepository.existsByCompanyIdAndDocumentNumber(companyId, request.getDocumentNumber())) {
            throw new DuplicateResourceException("ThirdParty", "documentNumber", request.getDocumentNumber());
        }

        ThirdParty thirdParty = new ThirdParty();
        thirdParty.setActive(true);

        mapRequestToEntity(request, thirdParty, companyId);

        ThirdParty saved = thirdPartyRepository.save(thirdParty);
        log.info("Third party created successfully with id: {} and document: {}",
                saved.getId(), saved.getDocumentNumber());

        return mapToResponseDTO(saved);
    }

    // =====================================================
    // READ
    // =====================================================

    @Transactional(readOnly = true)
    public Page<ThirdPartyResponseDTO> listAll(String searchTerm, Pageable pageable) {
        Long companyId = currentTenantId();

        log.debug("Listing third parties for company ID: {} with search term: {}", companyId, searchTerm);

        // ADAPTED: Optimized primitives call
        Page<ThirdParty> entities = (searchTerm != null && !searchTerm.trim().isEmpty())
                ? thirdPartyRepository.findBySearchTerm(companyId, searchTerm, pageable)
                : thirdPartyRepository.findByCompanyId(companyId, pageable);

        return entities.map(this::mapToResponseDTO);
    }

    @Transactional(readOnly = true)
    public ThirdPartyResponseDTO findById(Long id) {
        return mapToResponseDTO(findEntityById(id));
    }

    /**
     * Helper to get the actual entity for internal use enforcing tenant isolation
     */
    private ThirdParty findEntityById(Long id) {
        Long companyId = currentTenantId();

        log.debug("Finding third party by id: {} for company ID: {}", id, companyId);

        return thirdPartyRepository.findById(id)
                .filter(tp -> tp.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("ThirdParty", id));
    }

    /**
     * Users often search for third parties by name, not just document number
     */
    @Transactional(readOnly = true)
    public ThirdPartyResponseDTO getByLegalName(String legalName) {
        Long companyId = currentTenantId();

        log.debug("Searching third party by legal name: {} for company ID: {}", legalName, companyId);

        // ADAPTED: Clean primitive check
        ThirdParty entity = thirdPartyRepository
                .findByCompanyIdAndLegalName(companyId, legalName)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("ThirdParty with legal name '%s' not found", legalName)
                ));

        return mapToResponseDTO(entity);
    }

    /**
     * For importing third parties from external systems
     */
    @Transactional
    public List<ThirdPartyResponseDTO> createBulk(List<ThirdPartyRequest> requests) {
        Long companyId = currentTenantId();

        log.info("Creating {} third parties in bulk for company ID: {}", requests.size(), companyId);

        List<ThirdParty> entities = new ArrayList<>();
        Set<String> documentNumbers = new HashSet<>();

        // Validate all first
        for (ThirdPartyRequest request : requests) {
            // Check for duplicates within the batch
            if (documentNumbers.contains(request.getDocumentNumber())) {
                throw new DuplicateResourceException(
                        "ThirdParty", "documentNumber", request.getDocumentNumber() + " (duplicate in batch)"
                );
            }
            documentNumbers.add(request.getDocumentNumber());

            // Check for duplicates in database using optimized primitive query
            if (thirdPartyRepository.existsByCompanyIdAndDocumentNumber(companyId, request.getDocumentNumber())) {
                throw new DuplicateResourceException("ThirdParty", "documentNumber", request.getDocumentNumber());
            }

            ThirdParty entity = new ThirdParty();
            entity.setActive(true);
            mapRequestToEntity(request, entity, companyId);
            entities.add(entity);
        }

        // Save all at once
        List<ThirdParty> saved = thirdPartyRepository.saveAll(entities);
        log.info("Successfully created {} third parties in bulk", saved.size());

        return saved.stream().map(this::mapToResponseDTO).toList();
    }

    /**
     * Retrieves a third party by document number and maps it to a DTO.
     */
    @Transactional(readOnly = true)
    public ThirdPartyResponseDTO getByDocumentNumber(String documentNumber) {
        Long companyId = currentTenantId();

        log.debug("Finding third party by document number: {} for company ID: {}", documentNumber, companyId);

        // ADAPTED: Efficient primitive ID filtering
        ThirdParty entity = thirdPartyRepository
                .findByCompanyIdAndDocumentNumber(companyId, documentNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("ThirdParty with document number '%s' not found", documentNumber)
                ));

        return mapToResponseDTO(entity);
    }

    // =====================================================
    // UPDATE
    // =====================================================
    public ThirdPartyResponseDTO update(Long id, ThirdPartyRequest request) {
        Long companyId = currentTenantId();
        log.debug("Updating third party id: {} under tenant ID: {}", id, companyId);

        ThirdParty existing = findEntityById(id);

        // --- IDENTIFICATION SHIELD WITH AUDIT LOG ---
        if (!existing.getDocumentNumber().equals(request.getDocumentNumber())) {

            // 1. Check if the third party has accounting movements
            if (journalEntryRepository.existsByThirdParty(existing)) {
                log.error("AUDIT ALERT: NIT/Document changed for ThirdParty ID: {} from [{}] to [{}] " +
                                "| Reason: Manual correction on record with existing accounting movements.",
                        id, existing.getDocumentNumber(), request.getDocumentNumber());
            }

            // 2. Strict Rule: New document number must not belong to another existing third party
            if (thirdPartyRepository.existsByCompanyIdAndDocumentNumber(companyId, request.getDocumentNumber())) {
                throw new DuplicateResourceException(
                        "ThirdParty", "documentNumber", request.getDocumentNumber()
                );
            }
        }

        mapRequestToEntity(request, existing, companyId);

        ThirdParty updated = thirdPartyRepository.save(existing);
        log.info("Third party {} updated successfully", id);

        return mapToResponseDTO(updated);
    }

    /**
     * Soft Delete: Instead of removing from DB, we deactivate the record.
     */
    public void deactivate(Long id) {
        log.debug("Deactivating third party id: {}", id);

        ThirdParty tp = findEntityById(id);

        // Check if third party has any journal entry movements
        if (journalEntryRepository.existsByThirdParty(tp)) {
            throw new InvalidOperationException(
                    String.format("Cannot deactivate third party '%s' because it has accounting movements. " +
                                    "Contact your administrator if you need to archive this record.",
                            tp.getLegalDisplayName())
            );
        }

        tp.setActive(false);
        thirdPartyRepository.save(tp);

        log.info("Third party {} deactivated successfully", id);
    }

    public void activate(Long id) {
        log.debug("Activating third party id: {}", id);

        ThirdParty tp = findEntityById(id);
        tp.setActive(true);
        thirdPartyRepository.save(tp);

        log.info("Third party {} activated successfully", id);
    }

    /**
     * Calculates the Verification Digit (DV) for Colombian NIT using Modulo 11 algorithm.
     */
    private int calculateDV(String nit) {
        int[] primes = {3, 7, 13, 17, 19, 23, 29, 37, 41, 43, 47, 53, 59, 67, 71};
        int sum = 0;

        String cleanNit = nit.replaceAll("[^0-9]", "");
        if (cleanNit.isEmpty()) {
            log.warn("DV Calculation: NIT is empty after cleaning for input: {}", nit);
            return 0;
        }

        // --- OVERFLOW PROTECTION ---
        if (cleanNit.length() > primes.length) {
            log.error("NIT overflow: {} exceeds maximum calculation length of 15", cleanNit);
            throw new InvalidOperationException(
                    "The document number is too long for Verification Digit (DV) calculation. Max 15 digits allowed."
            );
        }

        for (int i = 0; i < cleanNit.length(); i++) {
            int digit = Character.getNumericValue(cleanNit.charAt(cleanNit.length() - 1 - i));
            sum += (digit * primes[i]);
        }

        int remainder = sum % 11;
        int dv = (remainder < 2) ? remainder : 11 - remainder;

        log.debug("Calculated DV for NIT {}: {}", cleanNit, dv);
        return dv;
    }

    /**
     * Maps request DTO to entity, including all validations
     */
    private void mapRequestToEntity(ThirdPartyRequest request, ThirdParty entity, Long companyId) {
        // Document standardization
        String cleanDoc = request.getDocumentNumber() != null ? request.getDocumentNumber().trim() : null;
        entity.setDocumentNumber(cleanDoc);
        entity.setDocumentType(request.getDocumentType());

        // Calculate verification digit for numeric documents (Colombian NIT)
        if (cleanDoc != null && cleanDoc.matches("\\d+")) {
            entity.setVerificationDigit(calculateDV(cleanDoc));
        }
        entity.setPersonType(request.getPersonType());

        // Box 36: TRADE NAME (Optional for everyone)
        entity.setTradeName(request.getTradeName() != null ?
                request.getTradeName().trim().toUpperCase() : null);

        // Box 35 & Names: LEGAL IDENTIFICATION
        if ("JURIDICA".equals(request.getPersonType()) || "LEGAL".equals(request.getPersonType())) {
            if (request.getBusinessName() == null || request.getBusinessName().trim().isEmpty()) {
                throw new InvalidOperationException("Business name (Razón Social) is required for legal entities.");
            }
            entity.setBusinessName(request.getBusinessName().trim().toUpperCase());
        } else if ("NATURAL".equals(request.getPersonType())) {
            if (request.getFirstName() == null || request.getLastName() == null) {
                throw new InvalidOperationException("First and Last names are required for natural persons.");
            }
            entity.setFirstName(request.getFirstName().trim().toUpperCase());
            entity.setMiddleName(request.getMiddleName() != null ? request.getMiddleName().trim().toUpperCase() : null);
            entity.setLastName(request.getLastName().trim().toUpperCase());
            entity.setSecondLastName(request.getSecondLastName() != null ? request.getSecondLastName().trim().toUpperCase() : null);

            entity.setBusinessName(request.getBusinessName() != null ?
                    request.getBusinessName().trim().toUpperCase() : null);
        }

        entity.setTaxRegime(request.getTaxRegime());

        // Contact Email normalization
        entity.setEmail(request.getEmail() != null ?
                request.getEmail().trim().toLowerCase() : null);

        // Billing Email normalization
        entity.setBillingEmail(request.getBillingEmail() != null ?
                request.getBillingEmail().trim().toLowerCase() : null);

        entity.setPhone(request.getPhone());
        entity.setMobile(request.getMobile());

        // Address normalization
        entity.setAddress(request.getAddress() != null ? request.getAddress().trim().toUpperCase() : null);

        // Validate and set City
        City city = cityRepository.findById(request.getCityId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("City with ID %d not found", request.getCityId())
                ));
        entity.setCity(city);

        // Validate and set default Cost Center (optional) restrict to current tenant primitive scope
        if (request.getDefaultCostCenterId() != null) {
            CostCenter cc = costCenterRepository.findById(request.getDefaultCostCenterId())
                    .filter(c -> c.getCompany().getId().equals(companyId))
                    .filter(CostCenter::isAllowsMovement)
                    .orElseThrow(() -> new InvalidOperationException(
                            "Invalid Cost Center: Either not found, doesn't belong to company, or doesn't allow movements."
                    ));
            entity.setDefaultCostCenter(cc);
        } else {
            entity.setDefaultCostCenter(null);
        }
    }

    /**
     * Maps entity to response DTO
     */
    private ThirdPartyResponseDTO mapToResponseDTO(ThirdParty entity) {
        if (entity == null) {
            return null;
        }

        ThirdPartyResponseDTO dto = new ThirdPartyResponseDTO();
        dto.setId(entity.getId());
        dto.setDocumentNumber(entity.getDocumentNumber());
        dto.setDocumentType(entity.getDocumentType());
        dto.setVerificationDigit(entity.getVerificationDigit());
        dto.setPersonType(entity.getPersonType());
        dto.setTaxRegime(entity.getTaxRegime());

        dto.setLegalDisplayName(entity.getLegalDisplayName());
        dto.setFullIdentity(entity.getFullIdentity());

        dto.setTradeName(entity.getTradeName());
        dto.setFirstName(entity.getFirstName());
        dto.setMiddleName(entity.getMiddleName());
        dto.setLastName(entity.getLastName());
        dto.setSecondLastName(entity.getSecondLastName());
        dto.setBusinessName(entity.getBusinessName());




        dto.setEmail(entity.getEmail());
        dto.setBillingEmail(entity.getBillingEmail());
        dto.setMobile(entity.getMobile());
        dto.setAddress(entity.getAddress());
        dto.setActive(entity.getActive());

        dto.setCityId(entity.getCity().getId());
        dto.setCityName(entity.getCity().getName());

        if (entity.getDefaultCostCenter() != null) {
            dto.setDefaultCostCenterId(entity.getDefaultCostCenter().getId());
            dto.setDefaultCostCenterName(entity.getDefaultCostCenter().getName());
        }

        return dto;
    }
}