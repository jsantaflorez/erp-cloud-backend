package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.ThirdPartyRequest;
import com.erp.erp_cloud.dto.ThirdPartyResponseDTO;
import com.erp.erp_cloud.entity.City;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.CostCenter;
import com.erp.erp_cloud.entity.ThirdParty;
import com.erp.erp_cloud.exception.DuplicateResourceException;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.exception.ResourceNotFoundException;
import com.erp.erp_cloud.repository.CostCenterRepository;
import com.erp.erp_cloud.repository.ThirdPartyRepository;
import com.erp.erp_cloud.repository.CityRepository;
import com.erp.erp_cloud.security.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
@Transactional
public class ThirdPartyService {

    private static final Logger log = LoggerFactory.getLogger(ThirdPartyService.class);

    private final ThirdPartyRepository thirdPartyRepository;
    private final CompanyContext companyContext;
    private final CityRepository cityRepository;
    private final CostCenterRepository costCenterRepository;

    // =====================================================
    // CREATE
    // =====================================================

    public ThirdPartyResponseDTO create(ThirdPartyRequest request) {
        Company company = companyContext.getCurrentCompany();

        log.debug("Creating third party with document number: {} for company: {}",
                request.getDocumentNumber(), company.getId());

        // Validate duplicate document number
        if (thirdPartyRepository.existsByCompanyAndDocumentNumber(company, request.getDocumentNumber())) {
            throw new DuplicateResourceException("ThirdParty", "documentNumber", request.getDocumentNumber());
        }

        ThirdParty thirdParty = new ThirdParty();
        thirdParty.setCompany(company);
        thirdParty.setActive(true);

        mapRequestToEntity(request, thirdParty);

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
        Company company = companyContext.getCurrentCompany();

        log.debug("Listing third parties for company: {} with search term: {}", company.getId(), searchTerm);

        Page<ThirdParty> entities = (searchTerm != null && !searchTerm.trim().isEmpty())
                ? thirdPartyRepository.findBySearchTerm(company, searchTerm, pageable)
                : thirdPartyRepository.findByCompany(company, pageable);

        return entities.map(this::mapToResponseDTO);
    }

    @Transactional(readOnly = true)
    public ThirdPartyResponseDTO findById(Long id) {
        return mapToResponseDTO(findEntityById(id));
    }

    /**
     * Helper to get the actual entity for internal use
     */
    private ThirdParty findEntityById(Long id) {
        Company company = companyContext.getCurrentCompany();

        log.debug("Finding third party by id: {} for company: {}", id, company.getId());

        return thirdPartyRepository.findById(id)
                .filter(tp -> tp.getCompany().getId().equals(company.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("ThirdParty", id));
    }

    /**
     * Retrieves a third party by document number and maps it to a DTO.
     * This is useful for the frontend to quickly find a customer/vendor by their ID.
     */
    @Transactional(readOnly = true)
    public ThirdPartyResponseDTO getByDocumentNumber(String documentNumber) {
        Company company = companyContext.getCurrentCompany();

        log.debug("Finding third party by document number: {} for company: {}", documentNumber, company.getId());

        // We find the entity first, ensuring it belongs to the current company
        ThirdParty entity = thirdPartyRepository
                .findByCompanyAndDocumentNumber(company, documentNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("ThirdParty with document number '%s' not found", documentNumber)
                ));

        // We map it to the ResponseDTO before returning to the controller
        return mapToResponseDTO(entity);
    }

    // =====================================================
    // UPDATE
    // =====================================================

    public ThirdPartyResponseDTO update(Long id, ThirdPartyRequest request) {
        log.debug("Updating third party id: {}", id);

        ThirdParty existing = findEntityById(id);
        mapRequestToEntity(request, existing);

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
        // Note: In the future, we will check for movements here
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
     * Maps request DTO to entity, including all validations
     */
    private void mapRequestToEntity(ThirdPartyRequest request, ThirdParty entity) {
        entity.setDocumentNumber(request.getDocumentNumber());
        entity.setDocumentType(request.getDocumentType());

        // Calculate verification digit for numeric documents (Colombian NIT)
        if (request.getDocumentNumber() != null && request.getDocumentNumber().matches("\\d+")) {
            entity.setVerificationDigit(calculateDV(request.getDocumentNumber()));
        }

        entity.setPersonType(request.getPersonType());
        entity.setTaxRegime(request.getTaxRegime());
        entity.setFirstName(request.getFirstName());
        entity.setMiddleName(request.getMiddleName());
        entity.setLastName(request.getLastName());
        entity.setSecondLastName(request.getSecondLastName());
        entity.setBusinessName(request.getBusinessName());
        entity.setEmail(request.getEmail());
        entity.setPhone(request.getPhone());
        entity.setMobile(request.getMobile());
        entity.setAddress(request.getAddress());

        // Validate and set City
        City city = cityRepository.findById(request.getCityId())
                .orElseThrow(() -> new ResourceNotFoundException("City", request.getCityId()));
        entity.setCity(city);

        // Validate and set default Cost Center (optional)
        if (request.getDefaultCostCenterId() != null) {
            CostCenter cc = costCenterRepository.findById(request.getDefaultCostCenterId())
                    .filter(c -> c.getCompany().getId().equals(entity.getCompany().getId()))
                    .filter(CostCenter::isAllowsMovement)
                    .orElseThrow(() -> new InvalidOperationException(
                            "Invalid Cost Center: Either not found, doesn't belong to company, or doesn't allow movements."
                    ));
            entity.setDefaultCostCenter(cc);
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

        // Using your entity helper methods
        dto.setLegalDisplayName(entity.getLegalDisplayName());
        dto.setFullIdentity(entity.getFullIdentity());

        dto.setEmail(entity.getEmail());
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

    /**
     * Calculates the Verification Digit (DV) for Colombian NIT using Modulo 11 algorithm.
     * @param nit The document number string.
     * @return The calculated single-digit integer.
     */
    private int calculateDV(String nit) {
        int[] primes = {3, 7, 13, 17, 19, 23, 29, 37, 41, 43, 47, 53, 59, 67, 71};
        int sum = 0;

        // Remove any non-numeric characters just in case
        String cleanNit = nit.replaceAll("[^0-9]", "");
        if (cleanNit.isEmpty()) {
            log.warn("Empty NIT after cleaning: {}", nit);
            return 0;
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
}