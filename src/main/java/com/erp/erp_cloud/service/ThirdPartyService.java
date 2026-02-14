package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.ThirdPartyRequest;
import com.erp.erp_cloud.dto.ThirdPartyResponseDTO;
import com.erp.erp_cloud.entity.City;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.CostCenter;
import com.erp.erp_cloud.entity.ThirdParty;
import com.erp.erp_cloud.repository.CostCenterRepository;
import com.erp.erp_cloud.repository.ThirdPartyRepository;
import com.erp.erp_cloud.repository.CityRepository;
import com.erp.erp_cloud.security.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
@Transactional
public class ThirdPartyService {

    private final ThirdPartyRepository thirdPartyRepository;
    private final CompanyContext companyContext;
    private final CityRepository cityRepository;
    private final CostCenterRepository costCenterRepository;

    // =====================================================
    // CREATE
    // =====================================================

    public ThirdPartyResponseDTO create(ThirdPartyRequest request) {
        Company company = companyContext.getCurrentCompany();

        if (thirdPartyRepository.existsByCompanyAndDocumentNumber(company, request.getDocumentNumber())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Third party already exists.");
        }

        ThirdParty thirdParty = new ThirdParty();
        thirdParty.setCompany(company);
        thirdParty.setActive(true);

        mapRequestToEntity(request, thirdParty);
        return mapToResponseDTO(thirdPartyRepository.save(thirdParty));
    }

    // =====================================================
    // READ
    // =====================================================


    @Transactional(readOnly = true)
    public Page<ThirdPartyResponseDTO> listAll(String searchTerm, Pageable pageable) {
        Company company = companyContext.getCurrentCompany();
        Page<ThirdParty> entities = (searchTerm != null && !searchTerm.trim().isEmpty())
                ? thirdPartyRepository.findBySearchTerm(company, searchTerm, pageable)
                : thirdPartyRepository.findByCompany(company, pageable);

        return entities.map(this::mapToResponseDTO);
    }

    @Transactional(readOnly = true)
    public ThirdPartyResponseDTO findById(Long id) {
        return mapToResponseDTO(findEntityById(id));
    }

    // Helper to get the actual entity for internal use
    private ThirdParty findEntityById(Long id) {
        Company company = companyContext.getCurrentCompany();
        return thirdPartyRepository.findById(id)
                .filter(tp -> tp.getCompany().getId().equals(company.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Third party not found"));
    }

    /**
     * Retrieves a third party by document number and maps it to a DTO.
     * This is useful for the frontend to quickly find a customer/vendor by their ID.
     */
    @Transactional(readOnly = true)
    public ThirdPartyResponseDTO getByDocumentNumber(String documentNumber) {
        Company company = companyContext.getCurrentCompany();

        // We find the entity first, ensuring it belongs to the current company
        ThirdParty entity = thirdPartyRepository
                .findByCompanyAndDocumentNumber(company, documentNumber)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Third party with document " + documentNumber + " not found"));

        // We map it to the ResponseDTO before returning to the controller
        return mapToResponseDTO(entity);
    }

    // =====================================================
    // UPDATE
    // =====================================================
    public ThirdPartyResponseDTO update(Long id, ThirdPartyRequest request) {
        ThirdParty existing = findEntityById(id);
        mapRequestToEntity(request, existing);
        return mapToResponseDTO(thirdPartyRepository.save(existing));
    }

    /**
     * Soft Delete: Instead of removing from DB, we deactivate the record.
     */
    public void deactivate(Long id) {
        ThirdParty tp = findEntityById(id);
        // Note: In the future, we will check for movements here
        tp.setActive(false);
        thirdPartyRepository.save(tp);
    }

    public void activate(Long id) {
        ThirdParty tp = findEntityById(id);
        tp.setActive(true);
        thirdPartyRepository.save(tp);
    }

    private void mapRequestToEntity(ThirdPartyRequest request, ThirdParty entity) {
        entity.setDocumentNumber(request.getDocumentNumber());
        entity.setDocumentType(request.getDocumentType());

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

        City city = cityRepository.findById(request.getCityId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "City not found."));
        entity.setCity(city);

        if (request.getDefaultCostCenterId() != null) {
            CostCenter cc = costCenterRepository.findById(request.getDefaultCostCenterId())
                    .filter(c -> c.getCompany().getId().equals(entity.getCompany().getId()))
                    .filter(CostCenter::isAllowsMovement)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Cost Center."));
            entity.setDefaultCostCenter(cc);
        }
    }

    private ThirdPartyResponseDTO mapToResponseDTO(ThirdParty entity) {
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
        if (cleanNit.isEmpty()) return 0;

        for (int i = 0; i < cleanNit.length(); i++) {
            int digit = Character.getNumericValue(cleanNit.charAt(cleanNit.length() - 1 - i));
            sum += (digit * primes[i]);
        }

        int remainder = sum % 11;
        if (remainder < 2) return remainder;
        return 11 - remainder;
    }

}