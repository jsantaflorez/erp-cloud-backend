package com.erp.erp_cloud.entity;

import com.erp.erp_cloud.enums.TaxRegime;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.util.Optional;

@Entity
@Table(
        name = "third_parties",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_company_document",
                        columnNames = {"company_id", "document_number"}
                )
        },
        indexes = {
                // Index for searching by business name (legal entities)
                @Index(name = "idx_third_party_business_name", columnList = "company_id, business_name"),

                // Index for searching by person name (natural persons)
                @Index(name = "idx_third_party_names", columnList = "company_id, first_name, last_name"),

                // Index for document type searches (e.g., "all NITs")
                @Index(name = "idx_third_party_doc_type", columnList = "company_id, document_type"),

                // Index for active status queries (common filter)
                @Index(name = "idx_third_party_active", columnList = "company_id, active")
        }

)
@Data
// 1. Evita el error de ByteBuddyInterceptor (Proxy de Hibernate)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ThirdParty implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    // 2. Ocultamos la empresa en la respuesta para evitar recursión infinita
    @JsonIgnore
    private Company company;

    @Column(name = "document_number", length = 20, nullable = false)
    private String documentNumber;

    @Column(name = "document_type", length = 5, nullable = false)
    private String documentType;

    @Column(name = "verification_digit")
    private Integer verificationDigit;

    @Column(name = "person_type", length = 20, nullable = false)
    private String personType;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_regime", nullable = false, length = 50)
    private TaxRegime taxRegime;

    @Column(name = "first_name", length = 50)
    private String firstName;

    @Column(name = "middle_name", length = 50)
    private String middleName;

    @Column(name = "last_name", length = 50)
    private String lastName;

    @Column(name = "second_last_name", length = 50)
    private String secondLastName;

    @Column(name = "business_name", length = 150)
    private String businessName;

    @Email
    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "mobile", length = 100)
    private String mobile;

    @Column(name = "phone", length = 100)
    private String phone;

    @Column(name = "address", length = 200)
    private String address;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id", nullable = false)

    private City city;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    // Optional: Default cost center for automatic accounting suggestions
// Useful for employees (department) or specific providers (projects)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cost_center_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "parent", "children"})
    private CostCenter defaultCostCenter;

    public String getLegalDisplayName() {
        // 1. Build the legal name parts
        String legalName = String.join(" ",
                Optional.ofNullable(firstName).orElse(""),
                Optional.ofNullable(middleName).orElse(""),
                Optional.ofNullable(lastName).orElse(""),
                Optional.ofNullable(secondLastName).orElse("")
        ).trim();

        // 2. Check if the legal name is valid (not empty or just spaces)
        if (!legalName.isBlank()) {
            return legalName;
        }

        // 3. Fallback to business name if legal name is missing
        if (this.businessName != null && !this.businessName.isBlank()) {
            return this.businessName;
        }

        return "Unknown Third Party";
    }
    public String getFullIdentity() {
        String id = (this.documentNumber != null) ? this.documentNumber : "No ID";
        return id + " - " + this.getLegalDisplayName();
    }
}