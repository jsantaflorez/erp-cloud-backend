package com.erp.erp_cloud.entity;
import com.erp.erp_cloud.enums.TaxRegime;
import jakarta.persistence.*;
import lombok.Data;
@Entity
@Table(
        name = "third_parties",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_company_document",
                        columnNames = {"company_id", "document_number"}
                )
        }
)
@Data
public class ThirdParty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "document_number", length = 20, nullable = false)
    private String documentNumber;

    @Column(name = "document_type", length = 5, nullable = false)
    private String documentType;

    @Column(name = "verification_digit")
    private Byte verificationDigit;

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

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "mobile", length = 20)
    private String mobile;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "address", length = 200)
    private String address;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id", nullable = false)
    private City cityCode;



    @Column(name = "active", nullable = false)
    private Boolean active = true;

}
