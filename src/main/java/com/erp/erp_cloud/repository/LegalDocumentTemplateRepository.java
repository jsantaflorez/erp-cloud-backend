package com.erp.erp_cloud.repository;

import com.erp.erp_cloud.entity.LegalDocumentTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LegalDocumentTemplateRepository extends JpaRepository<LegalDocumentTemplate, Long> {

    Optional<LegalDocumentTemplate> findByCompanyIdAndTemplateKey(Long companyId, String templateKey);

    List<LegalDocumentTemplate> findByCompanyIdOrderByTemplateKeyAsc(Long companyId);
}
