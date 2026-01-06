package com.erp.erp_cloud.dto;

import com.erp.erp_cloud.enums.TaxRegime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    private String lastName;
    private String secondLastName;
    private String businessName;

    private String email;
    private String phone;
    private String mobile;
    private String address;

    @NotNull(message = "City ID is required")
    private Long cityId;
}
