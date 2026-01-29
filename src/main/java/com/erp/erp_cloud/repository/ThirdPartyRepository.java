package com.erp.erp_cloud.repository;

import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.ThirdParty;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import java.util.List;
@Repository
public interface  ThirdPartyRepository extends JpaRepository<ThirdParty, Long> {

    Optional<ThirdParty> findByCompanyAndDocumentNumber(
            Company company,
            String documentNumber
    );

    boolean existsByCompanyAndDocumentNumber(
            Company company,
            String documentNumber
    );


    // List all for the company with pagination

    //List<ThirdParty> findByCompany(Company company);
    @Query("SELECT t FROM ThirdParty t WHERE t.company = :company AND (" +
            "LOWER(t.businessName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(t.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(t.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "t.documentNumber LIKE CONCAT('%', :searchTerm, '%'))")
    Page<ThirdParty> findBySearchTerm(@Param("company") Company company,
                                      @Param("searchTerm") String searchTerm,
                                      Pageable pageable);
    Page<ThirdParty> findByCompany(Company company, Pageable pageable);


}
