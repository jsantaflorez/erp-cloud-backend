package com.erp.erp_cloud.controller;

import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.service.ChartOfAccountsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chart-of-accounts")
@RequiredArgsConstructor
public class ChartOfAccountsController {

    private final ChartOfAccountsService service;

    // =====================================================
    // CREATE
    // =====================================================
    @PostMapping
    public ResponseEntity<ChartOfAccounts> create(
            @Valid @RequestBody ChartOfAccounts request
    ) {
        ChartOfAccounts account = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(account);
    }

    // =====================================================
    // READ
    // =====================================================
    @GetMapping("/{id}")
    public ResponseEntity<ChartOfAccounts> getById(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<ChartOfAccounts> getByCode(
            @PathVariable String code
    ) {
        return ResponseEntity.ok(service.findByCode(code));
    }

    /**
     * Cuentas raíz (nivel 1 / sin padre)
     */
    @GetMapping("/roots")
    public ResponseEntity<List<ChartOfAccounts>> getRoots() {
        return ResponseEntity.ok(service.listRoots());
    }

    /**
     * Hijos directos de una cuenta
     */
    @GetMapping("/{parentId}/children")
    public ResponseEntity<List<ChartOfAccounts>> getChildren(
            @PathVariable Long parentId
    ) {
        return ResponseEntity.ok(service.listChildren(parentId));
    }

    /**
     * Cuentas de movimiento activas
     * (para asientos contables)
     */
    @GetMapping("/posting")
    public ResponseEntity<List<ChartOfAccounts>> getPostingAccounts() {
        return ResponseEntity.ok(service.listPostingAccounts());
    }

    /**
     * Búsqueda por nombre o código
     */
    @GetMapping("/search")
    public ResponseEntity<List<ChartOfAccounts>> search(
            @RequestParam String q
    ) {
        return ResponseEntity.ok(service.search(q));
    }

    /**
     * Cuentas por nivel contable
     */
    @GetMapping("/level/{level}")
    public ResponseEntity<List<ChartOfAccounts>> getByLevel(
            @PathVariable Byte level
    ) {
        return ResponseEntity.ok(service.listByLevel(level));
    }

    // =====================================================
    // UPDATE
    // =====================================================
    @PutMapping("/{id}")
    public ResponseEntity<ChartOfAccounts> update(
            @PathVariable Long id,
            @Valid @RequestBody ChartOfAccounts request
    ) {
        return ResponseEntity.ok(service.update(id, request));
    }

    // =====================================================
    // ENABLE / DISABLE
    // =====================================================
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(
            @PathVariable Long id
    ) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activate(
            @PathVariable Long id
    ) {
        service.activate(id);
        return ResponseEntity.noContent().build();
    }
}
