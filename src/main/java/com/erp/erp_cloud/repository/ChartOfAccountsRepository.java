package com.erp.erp_cloud.repository;

import com.erp.erp_cloud.entity.ChartOfAccounts;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChartOfAccountsRepository extends JpaRepository<ChartOfAccounts, Long> {

    // 1. Buscar una cuenta por su código (fundamental para validaciones)
    Optional<ChartOfAccounts> findByCodigo(String codigo);

    // 2. Verificar si ya existe un código antes de crearlo
    boolean existsByCodigo(String codigo);

    // 3. Obtener todas las cuentas raíz (Nivel 1 o sin padre)
    List<ChartOfAccounts> findByPadreIsNullOrderByCodigoAsc();

    // 4. Obtener los hijos directos de una cuenta específica
    List<ChartOfAccounts> findByPadreIdCuentaOrderByCodigoAsc(Long idPadre);

    // 5. Obtener solo cuentas activas y de movimiento (para asientos contables)
    List<ChartOfAccounts> findByEsMovimientoTrueAndActivaTrueOrderByCodigoAsc();

    // 6. Búsqueda flexible para autocompletado (nombre o código parcial)
    List<ChartOfAccounts> findByNombreContainingIgnoreCaseOrCodigoContainingIgnoreCase(
            String nombre,
            String codigo
    );

    // 7. Obtener cuentas activas por nivel contable
    List<ChartOfAccounts> findByNivelAndActivaTrueOrderByCodigoAsc(Byte nivel);
}
