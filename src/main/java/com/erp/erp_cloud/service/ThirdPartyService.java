package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.ThirdPartyRequest;
import com.erp.erp_cloud.entity.City;
import com.erp.erp_cloud.entity.Company;
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
    private final CompanyContext companyContext; // contexto de empresa activa

    // =====================================================
    // CREATE
    // =====================================================
    public ThirdParty create(ThirdParty thirdParty) {

        Company company = companyContext.getCurrentCompany();

        boolean exists = thirdPartyRepository
                .existsByCompanyAndDocumentNumber(
                        company,
                        thirdParty.getDocumentNumber()
                );

        if (exists) {
            throw new IllegalStateException(
                    "Third party already exists for this company"
            );
        }

        thirdParty.setId(null); // seguridad
        thirdParty.setCompany(company);
        thirdParty.setActive(true);

        return thirdPartyRepository.save(thirdParty);
    }

    // =====================================================
    // READ
    // =====================================================
    @Transactional(readOnly = true)
    public List<ThirdParty> listAll() {
        // 1. Obtenemos la empresa del contexto (seguridad)
        Company company = companyContext.getCurrentCompany();

        // 2. Llamamos al método del repository que acabamos de crear
        return thirdPartyRepository.findByCompany(company);
    }

    @Transactional(readOnly = true)
    public ThirdParty getByDocumentNumber(String documentNumber) {

        Company company = companyContext.getCurrentCompany();

        return thirdPartyRepository
                .findByCompanyAndDocumentNumber(company, documentNumber)
                .orElseThrow(() ->
                        new IllegalStateException("Third party not found"));
    }
    @Transactional(readOnly = true)
    public ThirdParty findById(Long id) {
        Company company = companyContext.getCurrentCompany();

        return thirdPartyRepository.findById(id)
                .filter(tp -> tp.getCompany().getId().equals(company.getId()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Third party with ID " + id + " not found for this company"));

    }

    // =====================================================
    // UPDATE
    // =====================================================
    @Transactional
    public ThirdParty update(Long id, ThirdPartyRequest request) {
        // 1. Buscamos el tercero existente usando el método que ya validaba la empresa
        ThirdParty existing = findById(id);

        // 2. Actualizamos los campos desde el Request
        existing.setDocumentNumber(request.getDocumentNumber());
        existing.setDocumentType(request.getDocumentType());
        existing.setVerificationDigit(request.getVerificationDigit());
        existing.setPersonType(request.getPersonType());
        existing.setTaxRegime(request.getTaxRegime());
        existing.setFirstName(request.getFirstName());
        existing.setMiddleName(request.getMiddleName());
        existing.setLastName(request.getLastName());
        existing.setSecondLastName(request.getSecondLastName());
        existing.setBusinessName(request.getBusinessName());
        existing.setEmail(request.getEmail());
        existing.setPhone(request.getPhone());
        existing.setMobile(request.getMobile());
        existing.setAddress(request.getAddress());

        // 3. Actualizamos la ciudad
        City city = new City();
        city.setId(request.getCityId());
        existing.setCity(city);

        // 4. Guardamos los cambios
        return thirdPartyRepository.save(existing);
    }

    // =====================================================
    // ENABLE / DISABLE (ERP real)
    // =====================================================
    public void deactivate(Long id) {
        ThirdParty thirdParty = findById(id);
        thirdParty.setActive(false);
    }

    public void activate(Long id) {
        ThirdParty thirdParty = findById(id);
        thirdParty.setActive(true);
    }
}
