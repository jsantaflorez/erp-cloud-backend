package com.erp.erp_cloud.controller;

import com.erp.erp_cloud.dto.ApiResponse;
import com.erp.erp_cloud.dto.SiewinBalanceImportRequest;
import com.erp.erp_cloud.dto.SiewinBalanceImportResultDTO;
import com.erp.erp_cloud.security.UserPrincipal;
import com.erp.erp_cloud.service.SiewinBalanceImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Migración de saldos de apertura desde el plano de SIEWIN (RPV v.6).
 *
 * Deliberadamente en un controlador propio en vez de agregar un endpoint
 * a JournalEntryController: ese archivo ya tiene cambios locales sin
 * commitear que no tienen relación con esta migración (ver
 * claude/analisis-migracion-saldos-siewin-2026-11-12.md), así que un
 * controlador nuevo evita cualquier conflicto con ese trabajo en curso.
 *
 * Toda la lógica de parseo/validación/posteo vive en
 * SiewinBalanceImportService -- ver su Javadoc para el diseño completo.
 */
@RestController
@RequestMapping("/api/v1/journal-entries")
@RequiredArgsConstructor
@Tag(name = "Opening Balance Migration", description = "One-off migration of opening/closing balances from the legacy SIEWIN system")
public class OpeningBalanceImportController {

    private final SiewinBalanceImportService siewinBalanceImportService;

    // C1 (audit 2026-11-02): administrators only, see AuthorizationRules --
    // same gating as the other SIEWIN importers (PUC, Terceros).
    @PreAuthorize("@authz.isAdmin()")
    @PostMapping("/import-opening-balances-siewin")
    @Operation(
            summary = "Import opening balances from a SIEWIN closing-balance export",
            description = "Builds (or replaces) the company's single APERTURA journal entry from a SIEWIN " +
                    "RPV v.6 closing-balance export (parsed client-side) for the given fiscal year. Rejects " +
                    "the whole file with every validation problem collected at once if accounts/third-parties " +
                    "aren't found, cost-center breakdown is required, or the file's own subtotals don't match " +
                    "the recalculated leaves. Replaces any previous live APERTURA entry for this company by " +
                    "annulling it, never by editing it in place."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Opening balances imported successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Missing year, empty file, or source data integrity problems")
    public ResponseEntity<ApiResponse<SiewinBalanceImportResultDTO>> importOpeningBalancesSiewin(
            @RequestBody SiewinBalanceImportRequest request) {
        SiewinBalanceImportResultDTO result = siewinBalanceImportService.importFromSiewin(request, currentUsername());
        return ResponseEntity.ok(new ApiResponse<>("Opening balances imported successfully", true, result));
    }

    // NEW (2026-11-18): lets the screen warn that migrating again replaces
    // the existing APERTURA entry completely, manual edits included.
    @PreAuthorize("@authz.isAdmin()")
    @GetMapping("/opening-balance-status")
    @Operation(summary = "Whether the company already has a live APERTURA opening-balance entry")
    public ResponseEntity<ApiResponse<com.erp.erp_cloud.dto.OpeningBalanceStatusDTO>> openingBalanceStatus() {
        return ResponseEntity.ok(new ApiResponse<>("Opening balance status", true,
                siewinBalanceImportService.currentOpeningStatus()));
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getUsername();
        }
        return "system";
    }
}
