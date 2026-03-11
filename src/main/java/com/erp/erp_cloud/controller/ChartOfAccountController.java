package com.erp.erp_cloud.controller;

import com.erp.erp_cloud.dto.ApiResponse;
import com.erp.erp_cloud.dto.ChartOfAccountRequest;
import com.erp.erp_cloud.dto.ChartOfAccountResponseDTO;


import com.erp.erp_cloud.service.ChartOfAccountService;

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
@RequestMapping("/api/chart-of-accounts")
@RequiredArgsConstructor
public class ChartOfAccountController {

    private final ChartOfAccountService chartOfAccountService;

    /**
     * Create a new account entry.
     */



    @PostMapping
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
    public ResponseEntity<ApiResponse<ChartOfAccountResponseDTO>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(new ApiResponse<>("Account found", true, chartOfAccountService.findById(id)));
    }
    /**
     * Get account by accounting code.
     */

    @GetMapping("/code/{code}")
    public ResponseEntity<ApiResponse<ChartOfAccountResponseDTO>> getByCode(@PathVariable String code) {
        ChartOfAccountResponseDTO data = chartOfAccountService.findByCode(code);
        return ResponseEntity.ok(new ApiResponse<>("Account found", true, data));
    }

    /**
     * Get root accounts (Level 1).
     * Kept as List for tree-view purposes.
     */
    @GetMapping("/roots")
    public ResponseEntity<ApiResponse<List<ChartOfAccountResponseDTO>>> getRoots() {
        List<ChartOfAccountResponseDTO> data = chartOfAccountService.listRoots();
        return ResponseEntity.ok(new ApiResponse<>("Roots retrieved successfully", true, data));
    }


    /**
     * Get direct children of a specific account.
     */

    @GetMapping("/{parentId}/children")
    public ResponseEntity<ApiResponse<List<ChartOfAccountResponseDTO>>> getChildren(@PathVariable Long parentId) {
        List<ChartOfAccountResponseDTO> data = chartOfAccountService.listChildren(parentId);
        return ResponseEntity.ok(new ApiResponse<>("Children retrieved successfully", true, data));
    }


    /**
     * Get active accounts allowed for journal entries (posting accounts).
     */
    @GetMapping("/posting")
    public ResponseEntity<ApiResponse<Page<ChartOfAccountResponseDTO>>> getPostingAccounts(
            @PageableDefault(size = 20, sort = "code") Pageable pageable) {
        Page<ChartOfAccountResponseDTO> data = chartOfAccountService.listPostingAccounts(pageable);
        return ResponseEntity.ok(new ApiResponse<>("Posting Accounts retrieved successfully", true, data));
    }

    /**
     * Filter accounts by accounting level with pagination.
     */

    @GetMapping("/level/{level}")
    public ResponseEntity<ApiResponse<Page<ChartOfAccountResponseDTO>>> getByLevel(
            @PathVariable Byte level,
            @PageableDefault(size = 20, sort = "code") Pageable pageable) {

        Page<ChartOfAccountResponseDTO> data = chartOfAccountService.listByLevel(level, pageable);

        return ResponseEntity.ok(new ApiResponse<>("Accounts by level retrieved successfully", true, data));
    }


    /**
     * Update existing account details.
     */

    @PutMapping("/{id}")
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
     * Deactivate an account (Logical delete).
     */


    @DeleteMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        chartOfAccountService.deactivate(id);
        return ResponseEntity.ok(new ApiResponse<>("Account deactivated successfully", true, null));
    }


    /**
     * Activate a previously deactivated account.
     */


    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        chartOfAccountService.activate(id);
        return ResponseEntity.ok(new ApiResponse<>("Account activated successfully", true, null));

    }


}