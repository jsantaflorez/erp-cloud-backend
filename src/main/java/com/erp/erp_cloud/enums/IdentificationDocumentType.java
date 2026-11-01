package com.erp.erp_cloud.enums;

/**
 * Tipos de documento de identificacion segun el estandar DIAN vigente,
 * usados por {@code ThirdParty.documentType} (antes un {@code String}
 * libre de 5 caracteres, sin validacion real -- ver
 * propuesta-enum-tipo-documento-dian-2026-11-01.md, seccion 0 y 1).
 *
 * NOTA DE NOMBRE: existe ya un {@code com.erp.erp_cloud.entity.DocumentType}
 * en este proyecto -- es la entidad de TIPOS DE COMPROBANTE CONTABLE
 * (Factura de Venta, Recibo de Caja, etc., con numeracion consecutiva y
 * resolucion DIAN de facturacion), un concepto totalmente distinto al de
 * este enum (tipo de documento de IDENTIFICACION de una persona). Las dos
 * cosas incluso comparten alguna sigla por pura coincidencia (esa entidad
 * usa "CE"/"RC" como codigos de ejemplo de comprobante, sin relacion con
 * "Cedula de Extranjeria"/"Registro Civil" aqui). Por eso este enum se
 * llama {@code IdentificationDocumentType}, nunca {@code DocumentType} --
 * decision explicita del usuario (2026-11-01), para no crear ambiguedad
 * de nombre entre dos conceptos distintos.
 *
 * Nombres de constante = la sigla DIAN (igual a lo que ya guardaba
 * {@code third_parties.document_type} para los 4 valores que el
 * formulario manual permitia hasta ahora -- NIT, CC, CE, PP -- asi que
 * los terceros ya creados manualmente siguen siendo validos sin
 * migracion de datos, solo el ancho de columna cambia: ver
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
