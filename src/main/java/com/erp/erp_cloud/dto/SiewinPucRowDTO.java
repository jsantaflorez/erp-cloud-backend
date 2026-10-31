package com.erp.erp_cloud.dto;

/**
 * One row of the legacy SIEWIN chart of accounts ({@code t_puc_maestro26}),
 * exactly as parsed client-side from the client's export (.xlsx or .csv)
 * before being sent to POST /v1/chart-of-accounts/import-siewin.
 *
 * Deliberately mirrors the source table's own columns -- nivel1..nivel6,
 * nombre, imputable, bare, ceco -- rather than pre-computing the account
 * code or any derived field in the browser. All of that (digit-jump
 * re-derivation, category/nature resolution, fail-fast validation) is
 * backend logic in SiewinPucImportService, for the same reason the rest
 * of this codebase never trusts the client with business rules: every
 * field here is raw source data, nothing more.
 *
 * Levels are nullable Integers, not primitives: a level the source left
 * empty must be distinguishable from an explicit 0, even though in
 * practice SIEWIN always uses 0 for "not used" (verified against the real
 * sample) -- SiewinPucImportService treats null and 0 identically.
 *
 * alias and the ~15 other t_puc_maestro26 columns that were 100% zero/
 * empty across the entire sample file (pucm_retefuente, pucm_reteiva,
 * pucm_cta_pagos, pucm_cta_causacion, pucm_impuestos_consumo,
 * afecta_mod_activos, pucm_inrec3, pucm_codexo, pucm_conex,
 * pucm_costogasto_deducible, pucm_retencion, pucm_cc_fijo, pucm_ptje_*)
 * are deliberately NOT included here -- see
 * propuesta-importador-puc-siewin-2026-10-31.md section 3.3: there was no
 * real evidence to design a mapping for them in this first version, and
 * guessing one would be exactly the kind of generic shortcut this
 * importer's fail-fast philosophy exists to avoid.
 */
public record SiewinPucRowDTO(
        Integer nivel1,
        Integer nivel2,
        Integer nivel3,
        Integer nivel4,
        Integer nivel5,
        Integer nivel6,
        String nombre,
        // pucm_imputable: 1 = posting/movement account, 0 = header/group.
        Integer imputable,
        // pucm_bare: 6 = "cuenta de terceros" per the source DDL's own
        // comment, 0 otherwise -- maps to ChartOfAccountRequest.requiresThirdParty.
        Integer bare,
        // pucm_ceco: cost-center flag. 0 across the entire sample file,
        // mapped anyway (ceco != 0 -> requiresCostCenter) so a future
        // client's file that actually uses it isn't silently ignored.
        Integer ceco
) {
}
