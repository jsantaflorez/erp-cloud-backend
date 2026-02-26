package com.erp.erp_cloud.controller;


import com.erp.erp_cloud.dto.*;
import com.erp.erp_cloud.service.CostCenterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/cost-centers")
@RequiredArgsConstructor
public class CostCenterController {

    private final CostCenterService costCenterService;

    /**
     * Get all cost centers formatted as ResponseDTOs.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<CostCenterResponseDTO>>> getAll() {
        List<CostCenterResponseDTO> data = costCenterService.listAll();
        return ResponseEntity.ok(new ApiResponse<>("Cost centers retrieved successfully", true, data));
    }



    /**
     * Get only root-level centers for tree initialization.
     */
    @GetMapping("/roots")
    public ResponseEntity<ApiResponse<List<CostCenterResponseDTO>>> getRoots() {
        List<CostCenterResponseDTO> data = costCenterService.getRoots();
        return ResponseEntity.ok(new ApiResponse<>("Root cost centers retrieved", true, data));
    }


    /**
     * Get centers enabled for accounting transactions.
     */
    @GetMapping("/movement")
    public ResponseEntity<ApiResponse<List<CostCenterResponseDTO>>> getMovementAccounts() {
        List<CostCenterResponseDTO> data = costCenterService.getMovementAccounts();
        return ResponseEntity.ok(new ApiResponse<>("Movement cost Centers retrieved",true,data));

    }




    /**
     * Get child centers for a specific parent.
     */
    @GetMapping("/{parentId}/children")
    public ResponseEntity<ApiResponse<List<CostCenterResponseDTO>>> getChildren(@PathVariable Long parentId) {
        List<CostCenterResponseDTO> data = costCenterService.getChildren(parentId);
        return ResponseEntity.ok(new ApiResponse<>("Children retrieved successfully", true, data));
    }


    /**
     * Create a new cost center.
     */
    @PostMapping
      public ResponseEntity<ApiResponse<CostCenterResponseDTO>> create(@RequestBody CostCenterRequest request) {
        CostCenterResponseDTO created = costCenterService.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponse<>("Cost center created successfully", true, created));
    }




    /**
     * Update an existing cost center.
     */

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CostCenterResponseDTO>> update(@PathVariable Long id, @RequestBody CostCenterRequest request) {
        CostCenterResponseDTO updated = costCenterService.update(id, request);
        return ResponseEntity.ok(new ApiResponse<>("Cost center updated successfully", true, updated));
    }





    /**
     * Soft delete (Deactivate) a cost center.
     * We use 204 No Content to indicate success without a body.
     */
    @DeleteMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        costCenterService.deactivate(id);
        return ResponseEntity.ok(new ApiResponse<>("Cost Center deactivated successfully", true));
    }



/**
     * Re-activate a cost center if needed.
     */


    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        costCenterService.activate(id);
        return ResponseEntity.ok(new ApiResponse<>("Cost Center activated successfully", true));
    }

}
