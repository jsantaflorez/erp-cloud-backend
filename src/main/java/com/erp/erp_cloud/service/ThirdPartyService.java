package com.erp.erp_cloud.service;

import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.ThirdParty;
import com.erp.erp_cloud.repository.ThirdPartyRepository;
import com.erp.erp_cloud.security.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
                .filter(tp -> tp.getCompany().equals(company))
                .orElseThrow(() ->
                        new IllegalStateException("Third party not found"));
    }

    // =====================================================
    // UPDATE
    // =====================================================
    public ThirdParty update(Long id, ThirdParty data) {

        ThirdParty existing = findById(id);

        // ⚠️ El document number puede cambiar sin romper movimientos
        existing.setDocumentNumber(data.getDocumentNumber());
        existing.setDocumentType(data.getDocumentType());
        existing.setVerificationDigit(data.getVerificationDigit());
        existing.setPersonType(data.getPersonType());
        existing.setTaxRegime(data.getTaxRegime());

        existing.setFirstName(data.getFirstName());
        existing.setMiddleName(data.getMiddleName());
        existing.setLastName(data.getLastName());
        existing.setSecondLastName(data.getSecondLastName());
        existing.setBusinessName(data.getBusinessName());

        existing.setEmail(data.getEmail());
        existing.setMobile(data.getMobile());
        existing.setPhone(data.getPhone());
        existing.setAddress(data.getAddress());
        existing.setCity(data.getCity());

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
