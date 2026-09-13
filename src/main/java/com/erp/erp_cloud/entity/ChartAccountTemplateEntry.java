package com.erp.erp_cloud.entity;

import com.erp.erp_cloud.enums.ChartTemplateType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * One code -> suggested-name row in a reference chart-of-accounts template
 * (PUC Comercial, PUC Solidario, ...).
 *
 * Deliberately NOT tenant-scoped: this is shared reference data seeded once
 * (see the SQL script generated alongside this feature), completely
 * separate from ChartOfAccounts, which holds each company's real accounts.
 * A row here is only ever read to populate a suggestion in the "Crear
 * Cuenta" form -- it's never written to, referenced by foreign key, or
 * treated as authoritative.
 */
@Entity
@Table(
        name = "chart_account_templates",
        indexes = {
                @Index(name = "idx_chart_account_templates_type_code", columnList = "template_type, code")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ChartAccountTemplateEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "template_type", nullable = false, length = 20)
    private ChartTemplateType templateType;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;
}
