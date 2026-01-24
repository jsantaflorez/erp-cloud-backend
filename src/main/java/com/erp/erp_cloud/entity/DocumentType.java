package com.erp.erp_cloud.entity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


import java.io.Serializable;


@Entity
@Table(
        name = "document_types",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_document_type_company_code",
                        columnNames = {"company_id", "code"}
                )
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentType implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String code; // Ej: "FV", "CE", "RC"

    @Column(nullable = false, length = 100)
    private String name; // Ej: "Factura de Venta"

    @Column(name = "prefix", length = 10)
    private String prefix;// Optional: "INV-", "FACT-"

    // Initialize at 0 to avoid Nulls and facilitate increments
    @Column(name = "current_consecutive", nullable = false)
    private Long currentConsecutive = 0L;

    @Column(nullable = false)
    private boolean active = true;


    @Column(name = "is_accounting", nullable = false)
    private boolean accounting = true; // Lombok will generate getAccounting and setAccounting



    // IMPORTANT FOR AUDIT/LEGAL: Stores resolution numbers (e.g., DIAN)
    @Column(name = "legal_resolution", length = 255)
    private String legalResolution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore
    private Company company;

    // TODO: Link to accounting templates later [cite: 2026-01-22]
}