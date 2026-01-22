package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.ThirdPartyRequest;
import com.erp.erp_cloud.entity.City;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.CostCenter;
import com.erp.erp_cloud.entity.ThirdParty;
import com.erp.erp_cloud.repository.ThirdPartyRepository;
import com.erp.erp_cloud.security.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ThirdPartyService {

    private final ThirdPartyRepository thirdPartyRepository;
    private final CompanyContext companyContext;

    // =====================================================
    // CREATE
    // =====================================================
    public ThirdParty create(ThirdPartyRequest request) {
        // Get the current active company from security context
        Company company = companyContext.getCurrentCompany();

        // Check if the document number already exists for this company
        boolean exists = thirdPartyRepository
                .existsByCompanyAndDocumentNumber(company, request.getDocumentNumber());

        if (exists) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Third party already exists for this company");
        }

        ThirdParty thirdParty = new ThirdParty();

        // Map request data to the entity
        mapRequestToEntity(request, thirdParty);

        // Set mandatory context fields
        thirdParty.setCompany(company);
        thirdParty.setActive(true);

        return thirdPartyRepository.save(thirdParty);
    }

    // =====================================================
    // READ
    // =====================================================
    @Transactional(readOnly = true)
    public List<ThirdParty> listAll() {
        // Retrieve all third parties belonging to the current company
        Company company = companyContext.getCurrentCompany();
        return thirdPartyRepository.findByCompany(company);
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
    private void mapRequestToEntity(ThirdPartyRequest request, ThirdParty entity) {
        entity.setDocumentNumber(request.getDocumentNumber());
        entity.setDocumentType(request.getDocumentType());
        entity.setVerificationDigit(request.getVerificationDigit());
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

        // City handling (Referential integrity depends on cityId existing in DB)
        City city = new City();
        city.setId(request.getCityId());
        entity.setCity(city);

        // Optional: Link default Cost Center if provided
        if (request.getDefaultCostCenterId() != null) {
            CostCenter cc = new CostCenter();
            cc.setId(request.getDefaultCostCenterId());
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
}