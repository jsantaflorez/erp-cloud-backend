package com.erp.erp_cloud.entity;

import com.erp.erp_cloud.enums.TaxRegime;
import com.erp.erp_cloud.enums.ChartTemplateType;
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

    // FIX (2026-11-07): same bug class as defaultGainAccount/
    // defaultLossAccount below -- LAZY here throws
    // "org.hibernate.LazyInitializationException: could not
    // initialize proxy [City#n] - no Session" the moment anything
    // reads company.getCity() outside the one short transaction
    // TenantResolver.resolve() used to load this Company. Hit in
    // practice via WithholdingCertificateService.getCertificateData()
    // (company.getCity().getName() for the certificate's issue
    // city), but the root cause isn't specific to that caller --
    // any later @Transactional method that reads city through the
    // TenantContext-retained Company proxy would hit the same
    // thing, since that Session is long closed by then. EAGER
    // loads city as part of TenantResolver's own query, so there
    // is never a stale proxy to re-initialize.
    @ManyToOne(fetch = FetchType.EAGER)
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

    // Self-contained "data:image/...;base64,..." URI (2026-11-09) --
    // NOT a hosted http(s) URL. See V5__company_logo_and_signers.sql:
    // this column used to be a dead, never-written logo_url varchar(255)
    // -- renamed/retyped rather than left alongside a new column, since
    // nothing in the codebase ever read or wrote it.
    @Column(name = "logo_data_url", columnDefinition = "LONGTEXT")
    private String logoDataUrl;

    // Signature-block settings for legal documents (2026-11-09) -- see
    // claude/progreso-2026-11-09.md. Company-level, not per-template: a
    // legal representative/accountant doesn't change between document
    // types, so it's set once here and every legal-document template
    // (this certificate, and any future one) reuses the same values.
    // Two independently-optional slots, not a list -- the spec calls for
    // "una o dos columnas", never more. Either or both left null falls
    // back to the original unlabeled "REPRESENTANTE LEGAL"/"CONTADOR"
    // signature lines in the certificate skeleton.
    @Column(name = "signer1_name", length = 150)
    private String signer1Name;

    @Column(name = "signer1_role", length = 100)
    private String signer1Role;

    @Column(name = "signer1_id_number", length = 30)
    private String signer1IdNumber;

    @Column(name = "signer1_professional_license", length = 50)
    private String signer1ProfessionalLicense;

    @Column(name = "signer2_name", length = 150)
    private String signer2Name;

    @Column(name = "signer2_role", length = 100)
    private String signer2Role;

    @Column(name = "signer2_id_number", length = 30)
    private String signer2IdNumber;

    @Column(name = "signer2_professional_license", length = 50)
    private String signer2ProfessionalLicense;

    // =====================
    // ACCOUNTING
    // =====================

    // Optional per company: a cooperative or other non-commercial entity
    // will not use the commercial PUC template, so this stays nullable.
    // Null means no chart-of-accounts template is configured (no name
    // suggestions offered when creating Plan de Cuentas entries).
    @Enumerated(EnumType.STRING)
    @Column(name = "chart_template", length = 20)
    private ChartTemplateType chartTemplate;

    // NEW (2026-10-03): default Ganancia/Pérdida accounts suggested on the
    // year-end closing screen (Cierre de Año). Deliberately just a
    // SUGGESTION, never the account actually used -- the user confirms or
    // overrides both every time a specific year is closed, and what was
    // actually used for that year is stored on AccountingPeriod itself, not
    // here. This keeps a later change to the company default from silently
    // rewriting how past years were closed. Both nullable: a company with
    // no defaults configured yet simply shows no suggestion, the user must
    // pick explicitly.
    // FIX (2026-10-06): LAZY here throws
    // "org.hibernate.LazyInitializationException: could not initialize
    // proxy ... - no Session" the first time either field is actually
    // configured. Root cause is specific to Company, not a general LAZY
    // problem: TenantResolver.resolve() loads this exact Company entity
    // inside its OWN short @Transactional(readOnly = true) (one per
    // request, called once by TenantFilter), and TenantContext then
    // holds onto that same entity instance for the rest of the request
    // -- including inside later, separate @Transactional methods (e.g.
    // CompanyService.getCurrentCompany()). By the time one of those
    // later methods touches a LAZY association, the Hibernate Session
    // that originally loaded this Company is already closed, and a
    // lazy proxy can never be re-initialized by a different, newer
    // transaction. EAGER sidesteps this entirely: both accounts load as
    // part of the same single query TenantResolver already runs, so
    // there is no proxy left to go stale. (AccountingPeriod's own new
    // gainAccount/lossAccount/closingEntry fields don't need this --
    // those are always loaded and read inside the one @Transactional
    // method that uses them, never carried across request-scoped
    // ThreadLocal state the way Company is.)
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "default_gain_account_id")
    private ChartOfAccounts defaultGainAccount;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "default_loss_account_id")
    private ChartOfAccounts defaultLossAccount;
}
