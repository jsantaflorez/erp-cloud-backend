package com.erp.erp_cloud.repository;

import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChartOfAccountsRepository extends JpaRepository<ChartOfAccounts, Long> {

    // =====================================================
    // VALIDACIONES BÁSICAS (por empresa)
    // =====================================================

    Optional<ChartOfAccounts> findByCompanyAndCode(
            Company company,
            String code
    );

    boolean existsByCompanyAndCode(
            Company company,
            String code
    );

    // =====================================================
    // JERARQUÍA DEL PLAN DE CUENTAS
    // =====================================================

    // Cuentas raíz (sin padre) por empresa
    List<ChartOfAccounts> findByCompanyAndParentIsNullOrderByCodeAsc(
            Company company
    );

    // Hijos directos de una cuenta
    List<ChartOfAccounts> findByCompanyAndParentIdOrderByCodeAsc(
            Company company,
            Long parentId
    );

    // =====================================================
    // USO EN ASIENTOS CONTABLES
    // =====================================================

    // Solo cuentas activas y de movimiento (posting)
    List<ChartOfAccounts>
    findByCompanyAndPostingAccountTrueAndActiveTrueOrderByCodeAsc(
            Company company
    );

    // =====================================================
    // BÚSQUEDAS FUNCIONALES (UI)
    // =====================================================

    // Autocompletado por código o nombre
    List<ChartOfAccounts>
    findByCompanyAndNameContainingIgnoreCaseOrCompanyAndCodeContainingIgnoreCase(
            Company company1,
            String name,
            Company company2,
            String code
    );

    // Cuentas activas por nivel
    List<ChartOfAccounts>
    findByCompanyAndLevelAndActiveTrueOrderByCodeAsc(
            Company company,
            Byte level
    );
}
