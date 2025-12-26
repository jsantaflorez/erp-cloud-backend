package com.erp.erp_cloud.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "t_puc",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"codigo"})
        }
)
@Data
public class ChartOfAccounts {

    // =========================
    // IDENTIFICACIÓN
    // =========================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cuenta")
    private Long idCuenta;

    /**
     * Código contable (ej: 110505, 413505, etc.)
     * Único en todo el PUC
     */
    @Column(nullable = false, length = 20)
    private String codigo;

    /**
     * Nombre de la cuenta
     */
    @Column(nullable = false, length = 150)
    private String nombre;

    /**
     * Nivel contable (1–9 suele ser suficiente)
     */
    @Column(nullable = false)
    private Byte nivel;

    /**
     * Naturaleza contable:
     * D = Débito
     * C = Crédito
     */
    @Column(nullable = false, length = 1)
    private String naturaleza;

    // =========================
    // CLASIFICACIÓN CONTABLE
    // =========================

    /**
     * Clase contable:
     * ACTIVO, PASIVO, PATRIMONIO, INGRESO, GASTO, COSTO, ORDEN
     */
    @Column(nullable = false, length = 20)
    private String clase;

    /**
     * Tipo financiero:
     * Corriente, No Corriente, Resultados, etc.
     */
    @Column(length = 20)
    private String tipo;

    /**
     * Indica si la cuenta permite movimientos contables
     * true  = cuenta de movimiento
     * false = cuenta título
     */
    @Column(nullable = false)
    private Boolean esMovimiento;

    // =========================
    // REGLAS DE USO EN ASIENTOS
    // =========================

    /**
     * Indica si en los asientos exige tercero
     */
    @Column(nullable = false)
    private Boolean requiereTercero = false;

    /**
     * Indica si exige centro de costo
     */
    @Column(nullable = false)
    private Boolean requiereCentroCosto = false;

    /**
     * Indica si exige subcentro o proyecto
     */
    @Column(nullable = false)
    private Boolean requiereSubCentro = false;

    /**
     * Cuenta activa o inactiva
     */
    @Column(nullable = false)
    private Boolean activa = true;

    // =========================
    // JERARQUÍA DEL PUC
    // =========================

    /**
     * Cuenta padre (nivel superior)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_padre")
    @ToString.Exclude
    private ChartOfAccounts padre;

    /**
     * Cuentas hijas (niveles inferiores)
     */
    @OneToMany(
            mappedBy = "padre",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE},
            fetch = FetchType.LAZY
    )
    @ToString.Exclude
    private List<ChartOfAccounts> hijos = new ArrayList<>();
}
