package com.erp.erp_cloud.entity;

import com.erp.erp_cloud.enums.TaxCategory;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Entity;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;


import java.io.Serializable;
@Entity
@Table(
        name = "taxes",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_tax_company_code", columnNames = {"company_id", "code"}),
                @UniqueConstraint(name = "uk_tax_company_account", columnNames = {"company_id", "account_id"})
        }
)
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class Tax extends BaseEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    // Free text (2026-10-15, user request): the specific legal/fiscal
    // name of this tax -- "RETEFUENTE", "RETEICA", "IMPOCONSUMO",
    // "IMP_CARBONO", or anything a future tax reform invents. NEVER
    // branched on in code (see TaxCategory's javadoc for the full
    // reasoning) -- purely descriptive, shown in the UI and used only
    // to sub-group lines within the Informe de Retenciones/Fase 4
    // certificate. Widened to 30 chars (was 20) for headroom on longer
    // future names.
    @Column(nullable = false, length = 30)
    private String type;

    // NEW (2026-10-15, user request): the structural category that
    // actually drives report behavior -- see TaxCategory's javadoc.
    // This is what Fase 3 (Informe de IVA) and Fase 4 (Informe de
    // Retenciones) filter/group on; `type` above never is.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaxCategory category;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal rate;

    @Column(name = "requires_base", nullable = false)
    private boolean requiresBase = true;

    @Column(name = "minimum_base", precision = 15, scale = 2)
    private BigDecimal minimumBase = BigDecimal.ZERO;

    // D o C
    @Column(nullable = false, length = 1)
    private String sign;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    @JsonIgnoreProperties({"parent", "hibernateLazyInitializer", "handler"})
    private ChartOfAccounts account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore
    private Company company;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
