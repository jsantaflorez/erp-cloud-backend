package com.erp.erp_cloud.dto;

import java.util.List;

/**
 * Request body for POST /api/v1/chart-of-accounts/import-siewin.
 *
 * rows is the entire legacy SIEWIN export, already parsed client-side into
 * SiewinPucRowDTO -- see that class's javadoc for why the parsing stops at
 * "raw source columns, one row each" rather than doing any transformation
 * in the browser.
 */
public record SiewinPucImportRequest(List<SiewinPucRowDTO> rows, String mode) {

    /**
     * NEW (2026-11-17): "solo agregar cuentas nuevas". Se vuelve a subir el
     * PUC completo y actual exportado de SIEWIN sobre una compañía que YA
     * tiene plan de cuentas: se crean únicamente los códigos que no existen
     * y NO se modifica ninguna cuenta existente. Sin mode (o cualquier otro
     * valor) se mantiene el comportamiento original: importación completa,
     * solo con el plan vacío.
     */
    public static final String MODE_ADD_MISSING = "ADD_MISSING";

    /** Compatibilidad: importación completa (comportamiento original). */
    public SiewinPucImportRequest(List<SiewinPucRowDTO> rows) {
        this(rows, null);
    }

    public boolean isAddMissing() {
        return MODE_ADD_MISSING.equalsIgnoreCase(mode);
    }
}
