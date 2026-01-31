package com.erp.erp_cloud.controller;

import com.erp.erp_cloud.dto.ChartOfAccountRequest;
import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.service.ChartOfAccountsService;
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
public class ChartOfAccountsController {

    private final ChartOfAccountsService service;

    /**
     * Create a new account entry.
     */
    @PostMapping
    public ResponseEntity<ChartOfAccounts> create(@Valid @RequestBody ChartOfAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    /**
     * Get the full catalog or search with pagination.
     * This replaces the old getAll and search by text.
     */
    @GetMapping
    public ResponseEntity<Page<ChartOfAccounts>> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "code") Pageable pageable) {
        return ResponseEntity.ok(service.listAll(search, pageable));
    }

    /**
     * Get account by internal ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ChartOfAccounts> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    /**
     * Get account by accounting code.
     */
    @GetMapping("/code/{code}")
    public ResponseEntity<ChartOfAccounts> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(service.findByCode(code));
    }

    /**
     * Get root accounts (Level 1).
     * Kept as List for tree-view purposes.
     */
    @GetMapping("/roots")
    public ResponseEntity<List<ChartOfAccounts>> getRoots() {
        return ResponseEntity.ok(service.listRoots());
    }

    /**
     * Get direct children of a specific account.
     */
    @GetMapping("/{parentId}/children")
    public ResponseEntity<List<ChartOfAccounts>> getChildren(@PathVariable Long parentId) {
        return ResponseEntity.ok(service.listChildren(parentId));
    }

    /**
     * Get active accounts allowed for journal entries (posting accounts).
     */
    @GetMapping("/posting")
    public ResponseEntity<Page<ChartOfAccounts>> getPostingAccounts(
            @PageableDefault(size = 20, sort = "code") Pageable pageable) {
        return ResponseEntity.ok(service.listPostingAccounts(pageable));
    }

    /**
     * Filter accounts by accounting level with pagination.
     */
    @GetMapping("/level/{level}")
    public ResponseEntity<Page<ChartOfAccounts>> getByLevel(
            @PathVariable Byte level,
            @PageableDefault(size = 20, sort = "code") Pageable pageable) {
        return ResponseEntity.ok(service.listByLevel(level, pageable));
    }

    /**
     * Update existing account details.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ChartOfAccounts> update(
            @PathVariable Long id,
            @Valid @RequestBody ChartOfAccountRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    /**
     * Delete an account.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Deactivate an account (Logical delete).
     */
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Activate a previously deactivated account.
     */
    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        service.activate(id);
        return ResponseEntity.noContent().build();
    }
}