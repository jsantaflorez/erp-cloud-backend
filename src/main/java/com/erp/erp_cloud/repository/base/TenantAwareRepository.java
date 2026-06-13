package com.erp.erp_cloud.repository.base;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Base repository for all tenant-aware entities.
 * Enforces company-scoped queries — findAll() without companyId does not exist.
 * Extend this instead of JpaRepository for all business domain repositories.
 */
@NoRepositoryBean
public interface TenantAwareRepository<T, ID> extends JpaRepository<T, ID> {

    /**
     * Replaces the unsafe JpaRepository.findAll().
     * Always scoped to the current tenant's company ID to avoid redundant entity joins.
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.company.id = :companyId")
    Page<T> findAllByCompany(@Param("companyId") Long companyId, Pageable pageable);

    /**
     * Safe single-entity lookup — ensures the record belongs to the current tenant.
     * Prevents horizontal privilege escalation (user A accessing user B's records by ID).
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.id = :id AND e.company.id = :companyId")
    Optional<T> findByIdAndCompany(@Param("id") ID id, @Param("companyId") Long companyId);

    /**
     * Existence check scoped to tenant — used for duplicate validation.
     */
    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END " +
            "FROM #{#entityName} e WHERE e.id = :id AND e.company.id = :companyId")
    boolean existsByIdAndCompany(@Param("id") ID id, @Param("companyId") Long companyId);
}