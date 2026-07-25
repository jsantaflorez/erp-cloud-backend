package com.erp.erp_cloud.dto;
import lombok.Data;


import com.erp.erp_cloud.enums.TaxRegime;

import java.util.Optional;


/**
 * Data Transfer Object for sending Third Party information to the UI.
 * This avoids circular references and hides internal database structures.
 */
@Data
public class ThirdPartyResponseDTO {
    private Long id;
    private String documentNumber;
    private String documentType;
    private Integer verificationDigit;
    private String personType;
    private TaxRegime taxRegime;

    // These are the "Calculated" fields from your Entity logic
    private String legalDisplayName;
    private String tradeName;
    private String fullIdentity;


    private String firstName;
    private String middleName;
    private String lastName;
    private String secondLastName;
    private String businessName;


    // Contact information
    private String email;
    private String billingEmail;
    private String mobile;
    private String phone;
    private String address;

    // Status flag for our "Soft Delete" philosophy
    private boolean active;

    // Flattened references: We send IDs and Names instead of full Objects
    private Long cityId;
    private String cityName;

    private Long defaultCostCenterId;
    private String defaultCostCenterName;
}