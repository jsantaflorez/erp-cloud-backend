package com.erp.erp_cloud.repository;

import com.erp.erp_cloud.entity.UserRole;
import com.erp.erp_cloud.repository.base.TenantAwareRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * NEW (2026-10-29, Multiempresa Fase 3): direct CRUD on UserRole rows
 * themselves, needed by the user-management screen's "assign/retirar
 * roles" actions. Extends TenantAwareRepository since UserRole has its
 * own `company` relation (independent of which company any of its
 * roles happen to be scoped to) -- the same tenant-isolation base every
 * other business repository in this app uses.
 */
@Repository
public interface UserRoleRepository extends TenantAwareRepository<UserRole, Long> {

    /**
     * Every UserRole row for a company, with its User and Role already
     * fetched -- the user-management list screen needs both for every
     * row, so this avoids the classic N+1 (one query per row) that a
     * plain findByCompanyId() + lazy access would cause.
     */
    @Query("SELECT ur FROM UserRole ur " +
            "JOIN FETCH ur.user u " +
            "JOIN FETCH ur.role r " +
            "WHERE ur.company.id = :companyId " +
            "ORDER BY u.email, r.name")
    List<UserRole> findAllWithUserAndRoleByCompanyId(@Param("companyId") Long companyId);

    List<UserRole> findByUserIdAndCompanyId(Long userId, Long companyId);

    Optional<UserRole> findByUserIdAndRoleIdAndCompanyId(Long userId, Long roleId, Long companyId);

    boolean existsByUserIdAndRoleIdAndCompanyId(Long userId, Long roleId, Long companyId);

    boolean existsByUserIdAndCompanyId(Long userId, Long companyId);
}
