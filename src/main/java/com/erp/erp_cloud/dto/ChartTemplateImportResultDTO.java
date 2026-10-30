package com.erp.erp_cloud.dto;

import com.erp.erp_cloud.enums.ChartTemplateType;

/**
 * Summary returned after a successful "Importar PUC Estandar" run (Fase 4,
 * clonacion de PUC estandar). Purely informational -- the frontend reloads
 * the account tree separately; this is just what to tell the user.
 */
public record ChartTemplateImportResultDTO(
        ChartTemplateType templateType,
        int accountsCreated,
        int rootAccountsCreated,
        int memoAccountsSkipped
) {
}
