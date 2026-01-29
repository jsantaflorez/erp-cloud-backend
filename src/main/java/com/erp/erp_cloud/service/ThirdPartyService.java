package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.ThirdPartyRequest;
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


import java.util.List;

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
// =====================================================
// CREATE - Corregido para validación de Centros de Costo
// =====================================================
    public ThirdParty create(ThirdPartyRequest request) {
        // 1. Get the current active company from security context
        Company company = companyContext.getCurrentCompany();

        // 2. Check if the document number already exists for this company
        boolean exists = thirdPartyRepository
                .existsByCompanyAndDocumentNumber(company, request.getDocumentNumber());

        if (exists) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Third party already exists for this company");
        }

        ThirdParty thirdParty = new ThirdParty();

        // 3. SET MANDATORY CONTEXT FIRST
        // Mapping depends on the company being already set in the entity
        thirdParty.setCompany(company);
        thirdParty.setActive(true);

        // 4. NOW map request data to the entity
        // The validator inside will now find the company ID correctly
        mapRequestToEntity(request, thirdParty);

        return thirdPartyRepository.save(thirdParty);
    }
    // =====================================================
    // READ
    // =====================================================


    @Transactional(readOnly = true)
    public Page<ThirdParty> listAll(String searchTerm, Pageable pageable) {
        // Get the current active company from security context
        Company company = companyContext.getCurrentCompany();

        // If search term is present, use the search repository method
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            return thirdPartyRepository.findBySearchTerm(company, searchTerm, pageable);
        }

        // If no search term, return all third parties for the company with pagination
        return thirdPartyRepository.findByCompany(company, pageable);
    }

    @Transactional(readOnly = true)
    public ThirdParty findById(Long id) {
        Company company = companyContext.getCurrentCompany();


        // Find by ID and ensure it belongs to the active company
        return thirdPartyRepository.findById(id)
                .filter(tp -> tp.getCompany().getId().equals(company.getId()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Third party with ID " + id + " not found for this company"));
    }

    @Transactional(readOnly = true)
    public ThirdParty getByDocumentNumber(String documentNumber) {
        Company company = companyContext.getCurrentCompany();

        return thirdPartyRepository
                .findByCompanyAndDocumentNumber(company, documentNumber)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Third party with document " + documentNumber + " not found"));
    }

    // =====================================================
    // UPDATE
    // =====================================================
    public ThirdParty update(Long id, ThirdPartyRequest request) {
        // Validate existence and company ownership
        ThirdParty existing = findById(id);

        // Map updated data
        mapRequestToEntity(request, existing);

        return thirdPartyRepository.save(existing);
    }

    // =====================================================
    // HELPER METHODS (Private)
    // =====================================================
    /**
     * Maps data from a ThirdPartyRequest DTO to a ThirdParty Entity.
     * This avoids code duplication between create and update methods.
     */
    /**
     * Maps ThirdPartyRequest DTO to ThirdParty Entity with referential integrity checks.
     * This ensures that City and CostCenter exist in the database before assignment.
     */
    private void mapRequestToEntity(ThirdPartyRequest request, ThirdParty entity) {
        entity.setDocumentNumber(request.getDocumentNumber());
        entity.setDocumentType(request.getDocumentType());
        // If it's a NIT or professional document, we ensure the DV is correct
        if (request.getDocumentNumber() != null && request.getDocumentNumber().matches("\\d+")) {
            entity.setVerificationDigit(calculateDV(request.getDocumentNumber()));
        } else {
            entity.setVerificationDigit(request.getVerificationDigit());
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

        // 1. Validating City existence using cityRepository
        City city = cityRepository.findById(request.getCityId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "City with ID " + request.getCityId() + " not found."));
        entity.setCity(city);

        // 2. Validate Default Cost Center (The 3 Golden Rules)
        if (request.getDefaultCostCenterId() != null) {
            CostCenter cc = costCenterRepository.findById(request.getDefaultCostCenterId())
                    // Rule 1 & 2: Existence and Company Ownership
                    .filter(c -> c.getCompany().getId().equals(entity.getCompany().getId()))
                    // Rule 3: Must allow movement (Accounting integrity)
                    .filter(CostCenter::isAllowsMovement)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Invalid Cost Center: Must exist, belong to your company, and allow movement."));

            entity.setDefaultCostCenter(cc);
        } else {
            entity.setDefaultCostCenter(null);
        }



    }
    @Transactional
    public void delete(Long id) {
        // 1. Find the third party using the service's internal findById (validates company ownership)
        ThirdParty entity = this.findById(id);

        // 2. TODO: Check for accounting movements in the ledger [cite: 2026-01-17]
        // if (movementRepository.existsByThirdParty(entity)) {
        //    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
        //        "Cannot delete Third Party because it has linked accounting movements.");
        // }

        // 3. TODO: Check for initial balances in the Third Party Balance table [cite: 2026-01-17]
        // if (thirdPartyBalanceRepository.hasInitialBalance(entity)) {
        //    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
        //        "Cannot delete Third Party because it has an opening balance.");
        // }

        // 4. Perform the deletion
        thirdPartyRepository.delete(entity);
    }

    // =====================================================
    // STATUS MANAGEMENT
    // =====================================================
    public void deactivate(Long id) {
        ThirdParty tp = findById(id);
        tp.setActive(false);
    }

    public void activate(Long id) {
        ThirdParty tp = findById(id);
        tp.setActive(true);
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