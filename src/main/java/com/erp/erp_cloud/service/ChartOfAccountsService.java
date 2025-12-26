package com.erp.erp_cloud.service;

import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.repository.ChartOfAccountsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChartOfAccountsService {

    private final ChartOfAccountsRepository cuentaRepository;

    // =========================
    // CREAR CUENTA CONTABLE
    // =========================
    @Transactional
    public ChartOfAccounts crear(ChartOfAccounts cuenta, Long idPadre) {

        // 1. Código único
        if (cuentaRepository.existsByCodigo(cuenta.getCodigo())) {
            throw new IllegalArgumentException(
                    "Ya existe una cuenta contable con el código " + cuenta.getCodigo()
            );
        }

        // 2. Cuenta raíz
        if (idPadre == null) {
            cuenta.setPadre(null);
            cuenta.setNivel((byte) 1);
        }
        // 3. Cuenta hija
        else {
            ChartOfAccounts padre = cuentaRepository.findById(idPadre)
                    .orElseThrow(() ->
                            new IllegalArgumentException("La cuenta padre no existe")
                    );

            // 4. El padre NO puede ser de movimiento
            if (Boolean.TRUE.equals(padre.getEsMovimiento())) {
                throw new IllegalArgumentException(
                        "No se pueden crear subcuentas bajo una cuenta de movimiento"
                );
            }

            // 5. Nivel coherente
            cuenta.setNivel((byte) (padre.getNivel() + 1));

            // 6. Hereda naturaleza del padre
            cuenta.setNaturaleza(padre.getNaturaleza());

            cuenta.setPadre(padre);
        }

        // 7. Si es cuenta título, NO debe permitir movimientos
        if (Boolean.FALSE.equals(cuenta.getEsMovimiento())) {
            cuenta.setRequiereTercero(false);
            cuenta.setRequiereCentroCosto(false);
            cuenta.setRequiereSubCentro(false);
        }

        return cuentaRepository.save(cuenta);
    }

    // =========================
    // LISTAR CUENTAS RAÍZ
    // =========================
    @Transactional(readOnly = true)
    public List<ChartOfAccounts> obtenerRaices() {
        return cuentaRepository.findByPadreIsNullOrderByCodigoAsc();
    }

    // =========================
    // LISTAR HIJOS DE UNA CUENTA
    // =========================
    @Transactional(readOnly = true)
    public List<ChartOfAccounts> obtenerHijos(Long idPadre) {
        return cuentaRepository.findByPadreIdCuentaOrderByCodigoAsc(idPadre);
    }

    // =========================
    // CUENTAS USABLES EN ASIENTOS
    // =========================
    @Transactional(readOnly = true)
    public List<ChartOfAccounts> obtenerCuentasMovimiento() {
        return cuentaRepository.findByEsMovimientoTrueAndActivaTrueOrderByCodigoAsc();
    }

    // =========================
    // DESACTIVAR CUENTA
    // =========================
    @Transactional
    public void desactivar(Long idCuenta) {
        ChartOfAccounts cuenta = cuentaRepository.findById(idCuenta)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta no encontrada"));

        cuenta.setActiva(false);
        cuentaRepository.save(cuenta);
    }
}
