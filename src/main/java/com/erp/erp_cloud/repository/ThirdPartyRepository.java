package com.erp.erp_cloud.repository;

import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.ThirdParty;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface  ThirdPartyRepository extends JpaRepository<ThirdParty, Long> {

    Optional<ThirdParty> findByCompanyAndDocumentNumber(
            Company company,
            String documentNumber
    );

    boolean existsByCompanyAndDocumentNumber(
            Company company,
            String documentNumber
    );

    List<ThirdParty> findByCompany(Company company);
}
