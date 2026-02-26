package com.erp.erp_cloud.repository;



import com.erp.erp_cloud.entity.DocumentType;
import com.erp.erp_cloud.entity.Company;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;



@Repository
public interface DocumentTypeRepository extends JpaRepository<DocumentType, Long> {

    // Suggested methods for internal logic
    List<DocumentType> findByCompanyIdAndActiveTrue(Long companyId);
    Optional<DocumentType> findByCompanyIdAndCode(Long companyId, String code);


    boolean existsByCompanyAndCode(Company company, String code);

    List<DocumentType> findByCompanyAndNameContainingIgnoreCase(Company company, String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT dt FROM DocumentType dt WHERE dt.id = :id")
    Optional<DocumentType> findByIdWithLock(@Param("id") Long id);

}