package com.erp.erp_cloud.controller;


import com.erp.erp_cloud.dto.CostCenterRequest;
import com.erp.erp_cloud.dto.CostCenterResponseDTO;
import com.erp.erp_cloud.entity.CostCenter;
import com.erp.erp_cloud.service.CostCenterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/cost-centers")
@RequiredArgsConstructor
public class CostCenterController {

    private final CostCenterService service;

    /**
     * Get all cost centers formatted as ResponseDTOs.
     */
    @GetMapping
    public ResponseEntity<List<CostCenterResponseDTO>> getAll() {
        return ResponseEntity.ok(service.listAll());
    }

    /**
     * Get only root-level centers for tree initialization.
     */
    @GetMapping("/roots")
    public ResponseEntity<List<CostCenterResponseDTO>> getRoots() {
        return ResponseEntity.ok(service.getRoots());
    }

    /**
     * Get centers enabled for accounting transactions.
     */
    @GetMapping("/movement")
    public ResponseEntity<List<CostCenterResponseDTO>> getMovementAccounts() {
        return ResponseEntity.ok(service.getMovementAccounts());
    }
    /**
     * Get child centers for a specific parent.
     */
    @GetMapping("/{parentId}/children")
    public ResponseEntity<List<CostCenterResponseDTO>> getChildren(@PathVariable Long parentId) {
        return ResponseEntity.ok(service.getChildren(parentId));
    }

    /**
     * Create a new cost center.
     */
    @PostMapping
    public ResponseEntity<CostCenterResponseDTO> create(@RequestBody CostCenterRequest request) {
        return new ResponseEntity<>(service.create(request), HttpStatus.CREATED);
    }


    /**
     * Update an existing cost center.
     */
    @PutMapping("/{id}")
    public ResponseEntity<CostCenterResponseDTO> update(@PathVariable Long id, @RequestBody CostCenterRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }




    /**
     * Soft delete (Deactivate) a cost center.
     * We use 204 No Content to indicate success without a body.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        // Changed from hard delete to our new deactivate logic
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Re-activate a cost center if needed.
     */
    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        service.activate(id);
        return ResponseEntity.noContent().build();
    }


}