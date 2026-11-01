package com.erp.erp_cloud.dto;

/**
 * One raw row from a legacy SIEWIN third-party export
 * (CATALOGO_TERCEROS...xls / t_terceros), exactly as the frontend parses
 * it from the uploaded file -- no business logic applied yet. See
 * propuesta-importador-terceros-siewin-2026-10-31.md for the full
 * analysis behind which source columns are kept here and which are
 * deliberately left out.
 *
 * Fields kept, and why:
 * - documentNumber: source "ID." column, as text.
 * - documentTypeCode: source "0=CC,1=NIT,2=C.EXTR,3=T.I." column, the
 *   raw SIEWIN numeric code -- mapped to IdentificationDocumentType by
 *   SiewinThirdPartyImportService, never trusted directly.
 * - primerApellido/segundoApellido/primerNombre/segundoNombre/
 *   razonSocial: used both to derive personType (section 1 of the
 *   proposal) and as the actual name fields -- ThirdParty already has
 *   one column each, no concatenation needed.
 * - direccion, telefono: mapped directly to address/phone.
 * - codMunicipio: source "COD.MUNICIPIO", validated against the
 *   precargada `cities` catalog (see sql/2026-11-01... wait, the
 *   municipios load script is 2026-10-31_populate_cities_municipios.sql)
 *   after zero-padding to 5 digits.
 *
 * Deliberately NOT included (see proposal sections 2.1-2.3 for the
 * evidence behind each):
 * - "D.V.": ThirdPartyService.calculateDV() always recomputes this
 *   authoritatively for any numeric document; the source value is never
 *   imported or compared (11% of the real sample's NIT rows already
 *   disagreed with the Modulo-11 calculation).
 * - "0=P.JURIDICA,1=NATURAL": confirmed unreliable in 58% of the real
 *   sample's rows -- personType is derived from which name field is
 *   populated instead (razonSocial vs. primerApellido/primerNombre).
 * - "COD.PAIS": no source lookup table (t_paises) was provided, and
 *   ThirdParty has no country field of its own -- country is already
 *   implied through city -> country once the municipality resolves.
 */
public record SiewinThirdPartyRowDTO(
        String documentNumber,
        Integer documentTypeCode,
        String primerApellido,
        String segundoApellido,
        String primerNombre,
        String segundoNombre,
        String razonSocial,
        String direccion,
        Integer codMunicipio,
        String telefono
) {
}
