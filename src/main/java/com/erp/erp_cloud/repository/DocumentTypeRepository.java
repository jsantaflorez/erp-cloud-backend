package com.erp.erp_cloud.repository;



import com.erp.erp_cloud.entity.DocumentType;
import com.erp.erp_cloud.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentTypeRepository extends JpaRepository<DocumentType, Long> {

    // Find all document types for the current company
    List<DocumentType> findByCompany(Company company);

    // Find by code and company to avoid duplicates (e.g., two "FV" in the same company)
    Optional<DocumentType> findByCompanyAndCode(Company company, String code);

    // Check existence for validation
    boolean existsByCompanyAndCode(Company company, String code);

    // Search by name containing string and belonging to company
    List<DocumentType> findByCompanyAndNameContainingIgnoreCase(Company company, String name);
}