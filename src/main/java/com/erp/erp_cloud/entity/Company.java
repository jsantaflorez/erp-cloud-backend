package com.erp.erp_cloud.entity;

import com.erp.erp_cloud.enums.TaxRegime;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.io.Serializable;

@Entity
@Table(
        name = "t_companies",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"tax_id"}),
                @UniqueConstraint(columnNames = {"tenant_id"})
        }
)

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company implements Serializable {

    private static final long serialVersionUID = 1L;

    // =====================
    // IDENTITY
    // =====================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "company_id", nullable = false)
    private Long id;

    // =====================
    // GENERAL INFORMATION
    // =====================

    @Column(name = "legal_name", nullable = false, length = 150)
    private String legalName;

    @Column(name = "trade_name", length = 150)
    private String tradeName;

    // =====================
    // TAX IDENTIFICATION
    // =====================

    @Column(name = "tax_id", nullable = false, length = 20)
    private String taxId;

    @Column(name = "verification_digit", length = 2)
    private String verificationDigit;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_regime", nullable = false, length = 50)
    private TaxRegime taxRegime;

    // =====================
    // LOCATION
    // =====================



    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id", nullable = false)
    // @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private City city;


    // =====================
    // CONTACT
    // =====================

    @Column(name = "address", length = 200)
    private String address;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "email", nullable = false, length = 100)
    private String email;

    // =====================
    // MULTI-TENANT / CLOUD
    // =====================

    @Column(name = "tenant_id", nullable = false, length = 50, unique = true)
    private String tenantId;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "logo_url")
    private String logoUrl;
}
