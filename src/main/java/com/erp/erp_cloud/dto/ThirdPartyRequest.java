package com.erp.erp_cloud.dto;

import com.erp.erp_cloud.enums.TaxRegime;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data

public class ThirdPartyRequest {

    @NotBlank(message = "Document number is required")
    @Size(max = 15, message = "Document number cannot exceed 15 digits")
    @Pattern(regexp = "^[0-9]*$", message = "Document number must contain only digits")
    private String documentNumber;


    @NotBlank
    private String documentType;

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
