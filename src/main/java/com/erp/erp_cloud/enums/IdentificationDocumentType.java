package com.erp.erp_cloud.enums;

/**
 * Identification-document types per the current DIAN standard catalog,
 * used by {@code ThirdParty.documentType} (previously a free, unvalidated
 * 5-character {@code String} -- see
 * propuesta-enum-tipo-documento-dian-2026-11-01.md, sections 0 and 1).
 *
 * NAMING NOTE: this project already has a
 * {@code com.erp.erp_cloud.entity.DocumentType} entity -- that one is for
 * ACCOUNTING VOUCHER TYPES (Sales Invoice, Cash Receipt, etc., with
 * consecutive numbering and a DIAN billing resolution), a completely
 * different concept from this enum (a person's IDENTIFICATION document
 * type). The two even share an abbreviation or two by pure coincidence
 * (that entity uses "CE"/"RC" as example voucher codes, unrelated to
 * "Cedula de Extranjeria"/"Registro Civil" here). That's why this enum is
 * called {@code IdentificationDocumentType}, never {@code DocumentType} --
 * an explicit user decision (2026-11-01), to avoid a name collision
 * between two unrelated concepts.
 *
 * Constant names = the DIAN abbreviation (matching what
 * {@code third_parties.document_type} already stored for the 4 values the
 * manual form allowed until now -- NIT, CC, CE, PP -- so third parties
 * already created manually stay valid with no data migration, only the
 * column width changes: see
 * sql/2026-11-01_widen_third_parties_document_type.sql).
 */
public enum IdentificationDocumentType {
    RC("11", "Registro Civil"),
    TI("12", "Tarjeta de Identidad"),
    CC("13", "Cedula de Ciudadania"),
    TE("21", "Tarjeta de Extranjeria"),
    CE("22", "Cedula de Extranjeria"),
    NIT("31", "Numero de Identificacion Tributaria"),
    PP("41", "Pasaporte"),
    DIE("42", "Documento de Identificacion Extranjero"),
    PEP("47", "Permiso Especial de Permanencia"),
    PPT("48", "Permiso por Proteccion Temporal"),
    NIT_EXT("50", "NIT de otro pais");

    private final String dianCode;
    private final String description;

    IdentificationDocumentType(String dianCode, String description) {
        this.dianCode = dianCode;
        this.description = description;
    }

    public String getDianCode() {
        return dianCode;
    }

    public String getDescription() {
        return description;
    }
}
