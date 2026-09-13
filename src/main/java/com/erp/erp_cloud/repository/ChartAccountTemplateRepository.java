package com.erp.erp_cloud.repository;

import com.erp.erp_cloud.entity.ChartAccountTemplateEntry;
import com.erp.erp_cloud.enums.ChartTemplateType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChartAccountTemplateRepository extends JpaRepository<ChartAccountTemplateEntry, Long> {

    List<ChartAccountTemplateEntry> findByTemplateTypeOrderByCode(ChartTemplateType templateType);
}
