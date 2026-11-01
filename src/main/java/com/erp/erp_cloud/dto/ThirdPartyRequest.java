package com.erp.erp_cloud.dto;

import com.erp.erp_cloud.enums.TaxRegime;
import com.erp.erp_cloud.enums.IdentificationDocumentType;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data

public class ThirdPartyRequest {

    @NotBlank(message = "Document number is required")
    @Size(max = 15, message = "Document number cannot exceed 15 digits")
    @Pattern(regexp = "^[0-9]*$", message = "Document number must contain only digits")
    private String documentNumber;


    // CHANGED (2026-11-01): was a free String with no validation beyond
    // @NotBlank -- any text was accepted. Now a typed enum: an invalid
    // value fails deserialization with a 400 on its own, no manual check
    // needed here (same pattern already used by taxRegime below).
    @NotNull
    private IdentificationDocumentType documentType;

    @NotNull
    private TaxRegime taxRegime;

    @NotBlank
    private String personType;

    private Integer verificationDigit;

    @Size(max = 50, message = "FirstName field can store  up to 50 characters")
    private String firstName;
    private String middleName;
    @Size(max = 50, message = "LastName field can store  up to 100 characters")
    private String lastName;
    private String secondLastName;
    @Size(max = 150, message = "BusinessName field can store  up to 150 characters")
    private String businessName;
    @Size(max = 150, message = "TradeName field can store  up to 150 characters")
    private String tradeName;

    @Email(message = "Invalid email format")
    private String email;

    @Email(message = "Invalid email format")
    private String billingEmail;



    @Size(max = 100, message = "Mobile field can store multiple numbers up to 100 characters")
    private String phone;
    @Size(max = 100, message = "Mobile field can store multiple numbers up to 100 characters")
    private String mobile;
    private String address;

    @NotNull(message = "City ID is required")
    private Long cityId;

    private Long defaultCostCenterId;
}
