package com.erp.erp_cloud.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
@Entity
@Table(
        name = "chart_of_accounts",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_company_code",
                        columnNames = {"company_id", "code"}
                )
        }
)
@Data
public class ChartOfAccounts implements Serializable {

    // =========================
    // IDENTIFICACIÓN
    // =========================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Empresa dueña del plan de cuentas
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /**
     * Código contable (110505, 413505, etc.)
     * Único por empresa
     */
    @Column(name = "code", nullable = false, length = 20)
    private String code;

    /**
     * Nombre de la cuenta
     */
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    /**
     * Nivel contable
     */
    @Column(nullable = false)
    private Byte level;

    /**
     * Naturaleza contable:
     * D = Debit
     * C = Credit
     */
    @Column(nullable = false, length = 1)
    private String nature;

    // =========================
    // CLASIFICACIÓN CONTABLE
    // =========================

    @Column(nullable = false, length = 20)
    private String accountClass;

  //  Tipo financiero:   * Corriente, No Corriente, Resultados, etc.


    @Column(length = 20)
    private String accountType;

    /**
     * Cuenta de movimiento o título
     */
    @Column(nullable = false)
    private Boolean postingAccount;

    // =========================
    // REGLAS CONTABLES
    // =========================

    @Column(nullable = false)
    private Boolean requiresThirdParty = false;

    @Column(nullable = false)
    private Boolean requiresCostCenter = false;

    @Column(nullable = false)
    private Boolean requiresSubCostCenter = false;

    /**
     * Cuenta activa / inactiva
     */
    @Column(nullable = false)
    private Boolean active = true;

    // =========================
    // JERARQUÍA
    // =========================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private ChartOfAccounts parent;

    @OneToMany(
            mappedBy = "parent",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE},
            fetch = FetchType.LAZY
    )
    private List<ChartOfAccounts> children = new ArrayList<>();
}
