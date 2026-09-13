package com.erp.erp_cloud.enums;

/**
 * Which reference chart-of-accounts template (if any) a company uses to get
 * account-name suggestions when creating entries in the Plan de Cuentas.
 *
 * A company's Company.chartTemplate is nullable -- null means "no template",
 * the correct choice for any entity whose numbering doesn't match either of
 * these (e.g. a cooperative that isn't under Supersolidaria's standard PUC,
 * or any custom scheme). The suggestion feature simply doesn't activate in
 * that case; it never blocks or alters account creation.
 */
public enum ChartTemplateType {
    // Standard commercial PUC (Decreto 2650 de 1993 and later modifications).
    COMERCIAL,

    // PUC for entities in Colombia's "sector solidario" (cooperatives, etc.).
    SOLIDARIO
}
