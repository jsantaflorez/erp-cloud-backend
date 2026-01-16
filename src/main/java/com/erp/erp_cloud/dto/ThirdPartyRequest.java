package com.erp.erp_cloud.dto;

import com.erp.erp_cloud.enums.TaxRegime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data

public class ThirdPartyRequest {

    @NotBlank
    private String documentNumber;

    @NotBlank
    private String documentType;

    @NotNull
    private TaxRegime taxRegime;

    @NotBlank
    private String personType;

    private Integer verificationDigit;

    private String firstName;
    private String middleName;
    @Size(max = 50, message = "LastName field can store  up to 100 characters")
    private String lastName;
    private String secondLastName;
    @Size(max = 50, message = "BusinessName field can store  up to 150 characters")
    private String businessName;

    private String email;



    @Size(max = 100, message = "Mobile field can store multiple numbers up to 100 characters")
    private String phone;
    @Size(max = 100, message = "Mobile field can store multiple numbers up to 100 characters")
    private String mobile;
    private String address;

    @NotNull(message = "City ID is required")
    private Long cityId;

    private Long defaultCostCenterId;
}
