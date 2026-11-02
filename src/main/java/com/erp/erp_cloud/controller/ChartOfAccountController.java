package com.erp.erp_cloud.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.erp.erp_cloud.dto.*;


import com.erp.erp_cloud.enums.AccountCategory;
import com.erp.erp_cloud.enums.AccountClass;
import com.erp.erp_cloud.enums.FinancialStatement;
import com.erp.erp_cloud.service.ChartAccountTemplateImportService;
import com.erp.erp_cloud.service.ChartOfAccountService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/v1/chart-of-accounts")
@RequiredArgsConstructor
@Tag(name = "Chart of Accounts", description = "Endpoints for managing the Accounting Plan (PUC) and account hierarchy")
public class ChartOfAccountController {

    private final ChartOfAccountService chartOfAccountService;
    private final ChartAccountTemplateImportService chartAccountTemplateImportService;
    private final com.erp.erp_cloud.service.SiewinPucImportService siewinPucImportService;

    /**
     * Create a new account entry.
     */



    @PostMapping
    @Operation(summary = "Create a new accounting account", description = "Registers a new account in the chart of accounts for the current tenant.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "217", description = "Account created successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid input data or duplicate account code")
    public ResponseEntity<ApiResponse<ChartOfAccountResponseDTO>> create(@Valid @RequestBody ChartOfAccountRequest request) {
        ChartOfAccountResponseDTO created = chartOfAccountService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>("Account created successfully", true, created));
    }

    /**
     * Fase 4 (Multiempresa) -- clones the standard reference PUC
     * (ChartAccountTemplateEntry, COMERCIAL or SOLIDARIO) into real
     * ChartOfAccounts rows for the current tenant. Only allowed while the
     * Plan de Cuentas is completely empty -- see
     * ChartAccountTemplateImportService for the full design/assumptions.
     */
    // C1 (audit 2026-11-02): administrators only, see AuthorizationRules
    @PreAuthorize("@authz.isAdmin()")
    @PostMapping("/import-template")
    @Operation(
            summary = "Import the standard reference PUC as real accounts",
            description = "Clones every ChartAccountTemplateEntry row (COMERCIAL or SOLIDARIO) into " +
                    "ChartOfAccounts for the current tenant, respecting the existing hierarchy rules. " +
                    "Only available while the Plan de Cuentas is empty."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Template imported successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "No template selected, template mismatch, or Plan de Cuentas not empty")
    public ResponseEntity<ApiResponse<ChartTemplateImportResultDTO>> importTemplate(
            @RequestBody(required = false) ImportChartTemplateRequest request) {
        ChartTemplateImportResultDTO result = chartAccountTemplateImportService.importTemplate(request);
        return ResponseEntity.ok(new ApiResponse<>("Template imported successfully", true, result));
    }

    /**
     * Legacy-PUC migration from a client's SIEWIN export (t_puc_maestro26).
     * Unrelated to importTemplate() above -- this is a one-off migration
     * utility for a client whose own numbering doesn't match either
     * reference template, not a 3rd ChartTemplateType. The frontend parses
     * the uploaded .xlsx/.csv client-side and sends the parsed rows here
     * as plain JSON -- see SiewinPucImportService for the full digit-jump
     * re-derivation, validation and category/nature resolution rules.
     */
    // C1 (audit 2026-11-02): administrators only, see AuthorizationRules
    @PreAuthorize("@authz.isAdmin()")
    @PostMapping("/import-siewin")
    @Operation(
            summary = "Import a legacy PUC exported from SIEWIN",
            description = "Re-derives account codes from a SIEWIN t_puc_maestro26 export (parsed client-side) " +
                    "and creates real ChartOfAccounts rows for the current tenant. Only available while the " +
                    "Plan de Cuentas is empty; fails with every validation problem collected at once if the " +
                    "source data has any gap, duplicate, orphan or unmapped group."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "SIEWIN PUC imported successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Plan de Cuentas not empty, empty file, or source data integrity problems")
    public ResponseEntity<ApiResponse<com.erp.erp_cloud.dto.SiewinPucImportResultDTO>> importSiewin(
            @RequestBody com.erp.erp_cloud.dto.SiewinPucImportRequest request) {
        com.erp.erp_cloud.dto.SiewinPucImportResultDTO result = siewinPucImportService.importFromSiewin(request);
        return ResponseEntity.ok(new ApiResponse<>("SIEWIN PUC imported successfully", true, result));
    }

    /**
     * Get the full catalog or search with pagination.
     * This replaces the old getAll and search by text.
     */
    @GetMapping
    @Operation(summary = "List/Search accounts with pagination", description = "Retrieves a paginated list of accounts. Can be filtered by a search string (code or name).")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Data retrieved successfully")
    public ResponseEntity<ApiResponse<Page<ChartOfAccountResponseDTO>>> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "code") Pageable pageable) {
        Page<ChartOfAccountResponseDTO> data = chartOfAccountService.listAll(search, pageable);
        return ResponseEntity.ok(new ApiResponse<>("Data retrieved", true, data));
    }

    /**
     * Get account by internal ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get account by ID", description = "Retrieves detailed information of a specific account using its internal database ID.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Account found")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Account not found")
    public ResponseEntity<ApiResponse<ChartOfAccountResponseDTO>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(new ApiResponse<>("Account found", true, chartOfAccountService.findById(id)));
    }
    /**
     * Get account by accounting code.
     */

    @GetMapping("/code/{code}")
    @Operation(summary = "Get account by accounting code", description = "Retrieves an account using its official accounting code (e.g., '110505').")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Account found")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Account not found")
    public ResponseEntity<ApiResponse<ChartOfAccountResponseDTO>> getByCode(@PathVariable String code) {
        ChartOfAccountResponseDTO data = chartOfAccountService.findByCode(code);
        return ResponseEntity.ok(new ApiResponse<>("Account found", true, data));
    }

    /**
     * Get root accounts (Level 1).
     * Kept as List for tree-view purposes.
     */
    @GetMapping("/roots")
    @Operation(summary = "Get top-level accounts", description = "Retrieves all accounts at Level 1 (Assets, Liabilities, etc.) for tree-view purposes.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Roots retrieved successfully")
    public ResponseEntity<ApiResponse<List<ChartOfAccountResponseDTO>>> getRoots() {
        List<ChartOfAccountResponseDTO> data = chartOfAccountService.listRoots();
        return ResponseEntity.ok(new ApiResponse<>("Roots retrieved successfully", true, data));
    }


    /**
     * Get direct children of a specific account.
     */

    @GetMapping("/{parentId}/children")
    @Operation(summary = "Get children of a parent account", description = "Retrieves all direct sub-accounts for a given parent account ID.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Children retrieved successfully")
    public ResponseEntity<ApiResponse<List<ChartOfAccountResponseDTO>>> getChildren(@PathVariable Long parentId) {
        List<ChartOfAccountResponseDTO> data = chartOfAccountService.listChildren(parentId);
        return ResponseEntity.ok(new ApiResponse<>("Children retrieved successfully", true, data));
    }


    /**
     * Get active accounts allowed for journal entries (posting accounts).
     */
    @GetMapping("/posting")
    @Operation(summary = "Get auxiliary/posting accounts", description = "Retrieves accounts that allow direct journal entries (typically the lowest level in the hierarchy).")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Posting Accounts retrieved successfully")
    public ResponseEntity<ApiResponse<Page<ChartOfAccountResponseDTO>>> getPostingAccounts(
            @PageableDefault(size = 20, sort = "code") Pageable pageable) {
        Page<ChartOfAccountResponseDTO> data = chartOfAccountService.listPostingAccounts(pageable);
        return ResponseEntity.ok(new ApiResponse<>("Posting Accounts retrieved successfully", true, data));
    }

    /**
     * Filter accounts by accounting level with pagination.
     */

    @GetMapping("/level/{level}")
    @Operation(summary = "Filter accounts by depth level", description = "Retrieves accounts filtered by their hierarchy level (1 for Class, 2 for Group, etc.).")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Accounts by level retrieved successfully")
    public ResponseEntity<ApiResponse<Page<ChartOfAccountResponseDTO>>> getByLevel(
            @PathVariable Byte level,
            @PageableDefault(size = 20, sort = "code") Pageable pageable) {

        Page<ChartOfAccountResponseDTO> data = chartOfAccountService.listByLevel(level, pageable);

        return ResponseEntity.ok(new ApiResponse<>("Accounts by level retrieved successfully", true, data));
    }

    // Actualiza el método getMetadata() en ChartOfAccountsController
// (agregado: orden por displayOrder + accountClass en cada categoría)

    @GetMapping("/metadata")
    @Operation(summary = "Get Chart of Accounts enum metadata")
    public ResponseEntity<ApiResponse<ChartOfAccountsMetadataDTO>> getMetadata() {
        List<EnumOptionDTO> classes = Arrays.stream(AccountClass.values())
                .map(c -> new EnumOptionDTO(c.name(), c.getDisplayName(), c.getDisplayNameEs()))
                .toList();

        // Ordenadas por displayOrder (ya definido en el enum) y con el
        // accountClass al que pertenecen, para que el frontend pueda filtrar
        // las categorías según la clase elegida.
        List<CategoryOptionDTO> categories = Arrays.stream(AccountCategory.values())
                .sorted(Comparator.comparingInt(AccountCategory::getDisplayOrder))
                .map(c -> new CategoryOptionDTO(
                        c.name(), c.getDisplayName(), c.getDisplayNameEs(), c.getAccountClass().name()))
                .toList();

        List<EnumOptionDTO> statements = Arrays.stream(FinancialStatement.values())
                .map(s -> new EnumOptionDTO(s.name(), s.getDisplayName(), s.getDisplayNameEs()))
                .toList();

        ChartOfAccountsMetadataDTO metadata = new ChartOfAccountsMetadataDTO(classes, categories, statements);
        return ResponseEntity.ok(new ApiResponse<>("Metadata retrieved successfully", true, metadata));
    }


    /**
     * Update existing account details.
     */

    @PutMapping("/{id}")
    @Operation(summary = "Update an account", description = "Updates the description or properties of an existing account.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Account updated successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Account not found")
    public ResponseEntity<ApiResponse<ChartOfAccountResponseDTO>> update(
            @PathVariable Long id,
            @Valid @RequestBody ChartOfAccountRequest request) {
        ChartOfAccountResponseDTO updated = chartOfAccountService.update(id, request);
        return ResponseEntity.ok(new ApiResponse<>("Account updated successfully", true, updated));
    }

//    /**
//     * Delete an account.
//     */
//    @DeleteMapping("/{id}")
//    public ResponseEntity<Void> delete(@PathVariable Long id) {
//        chartOfAccountsService.delete(id);
//        return ResponseEntity.noContent().build();
//    }



    /**
     * Deactivate an account (Logical delete — sets active=false).
     * NOTE: This is a soft state change, not a real deletion — historical
     * transactions are always preserved. Uses PATCH (not DELETE) because
     * nothing is actually removed.
     *
     * TODO(hard-delete): a genuine DELETE endpoint may be added in the future
     * for accounts that have NEVER had any movements or opening balances —
     * e.g. DELETE /{id}, guarded by a check like
     * !repository.hasTransactions(id) && !repository.hasOpeningBalance(id).
     * This must remain a completely separate, stricter operation from
     * deactivate(); an account with any historical activity must never be
     * hard-deleted, only deactivated.
     */
    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate account", description = "Performs a logical delete by setting the account as inactive.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Account deactivated successfully")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        chartOfAccountService.deactivate(id);
        return ResponseEntity.ok(new ApiResponse<>("Account deactivated successfully", true, null));
    }



    /**
     * Activate a previously deactivated account.
     */


    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate account", description = "Restores a previously deactivated account to active status.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Account activated successfully")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        chartOfAccountService.activate(id);
        return ResponseEntity.ok(new ApiResponse<>("Account activated successfully", true, null));

    }


}