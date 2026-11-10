package com.erp.erp_cloud.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.io.Serializable;

/**
 * A company's own editable copy of a legal-document text template
 * (2026-11-07) -- first use case is the Withholding Certificate
 * ("Certificado de Retencion"), but the table is deliberately generic
 * so future legal documents (certificado de ingresos y retenciones,
 * paz y salvo, etc.) can reuse it without another migration.
 *
 * {@code templateKey} is a stable, code-defined key (e.g.
 * "WITHHOLDING_CERTIFICATE") -- the SAME split already used for
 * Tax.category/Tax.type (2026-10-15): the key decides which report/
 * screen a template feeds, never the free text inside it.
 *
 * {@code bodyHtml}/{@code footerHtml} are accountant-edited HTML with
 * {{variable}} placeholders, resolved by Mustache at render time --
 * the code that renders a certificate never branches on their content,
 * only substitutes into it. A company with no row yet for a given key
 * is NOT an error: WithholdingCertificateService/LegalDocumentTemplateService
 * fall back to a built-in default template shipped as a classpath
 * resource, so the feature works from day one without forcing setup
 * first. Saving from the admin screen performs the first INSERT.
 */
@Entity
@Table(
        name = "legal_document_templates",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_legal_template_company_key", columnNames = {"company_id", "template_key"})
        }
)
@Data
@EqualsAndHashCode(callSuper = true)
@Audited
public class LegalDocumentTemplate extends BaseEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private Company company;

    @Column(name = "template_key", nullable = false, length = 40)
    private String templateKey;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "document_title", length = 200)
    private String documentTitle;

    @Column(name = "body_html", nullable = false, columnDefinition = "LONGTEXT")
    private String bodyHtml;

    @Column(name = "footer_html", columnDefinition = "LONGTEXT")
    private String footerHtml;

    @Column(nullable = false)
    private boolean active = true;
}
