package com.erp.erp_cloud.enums;


public enum TaxRegime {
    // 1. Responsable de IVA (Antiguo Régimen Común)
    VAT_REGISTERED,

    // 2. No Responsable de IVA (Antiguo Régimen Simplificado)
    VAT_NOT_REGISTERED,

    // 3. Gran Contribuyente (Opcional, pero muy común en ERPs)
    GRAND_TAXPAYER,

    // 4. Régimen Especial (Entidades sin ánimo de lucro)
    SPECIAL_REGIME,

    // 5. Para Terceros: Persona Natural
    INDIVIDUAL,

    // 6. Para Terceros: Persona Jurídica / Sociedad
    CORPORATE
}