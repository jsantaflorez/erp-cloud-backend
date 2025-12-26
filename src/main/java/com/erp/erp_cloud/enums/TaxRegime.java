package com.erp.erp_cloud.enums;
public enum TaxRegime {
    SIMPLIFIED("Régimen Simplificado / No responsable de IVA"),
    COMMON("Régimen Común / Responsable de IVA"),
    GRAND_TAXPAYER("Gran Contribuyente"),
    SPECIAL("Régimen Especial"),
    INDIVIDUAL("Persona Natural"),
    CORPORATE("Persona Jurídica / Sociedad");

    private final String description;

    TaxRegime(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}