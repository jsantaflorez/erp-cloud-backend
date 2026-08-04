package com.erp.erp_cloud.controller;

import com.erp.erp_cloud.dto.ApiResponse;
import com.erp.erp_cloud.dto.ChartOfAccountRequest;
import com.erp.erp_cloud.dto.ChartOfAccountResponseDTO;


import com.erp.erp_cloud.dto.ChartOfAccountsMetadataDTO;
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


import java.util.List;

@RestController
@RequestMapping("/api/v1/chart-of-accounts")
@RequiredArgsConstructor
@Tag(name = "Chart of Accounts", description = "Endpoints for managing the Accounting Plan (PUC) and account hierarchy")
public class ChartOfAccountController {

    private final ChartOfAccountService chartOfAccountService;

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