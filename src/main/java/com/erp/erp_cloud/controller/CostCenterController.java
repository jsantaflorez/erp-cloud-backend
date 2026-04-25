package com.erp.erp_cloud.controller;


import com.erp.erp_cloud.dto.*;
import com.erp.erp_cloud.service.CostCenterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.util.List;



@RestController
@RequestMapping("/api/v1/cost-centers")
@RequiredArgsConstructor
@Tag(name = "Cost Centers", description = "Endpoints for managing organizational cost centers and hierarchical reporting structures")
public class CostCenterController {

    private final CostCenterService costCenterService;

    /**
     * Get all cost centers formatted as ResponseDTOs.
     */
    @GetMapping
    @Operation(summary = "Get all cost centers", description = "Retrieves the complete list of cost centers for the current tenant.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cost centers retrieved successfully")
    public ResponseEntity<ApiResponse<List<CostCenterResponseDTO>>> getAll() {
        List<CostCenterResponseDTO> data = costCenterService.listAll();
        return ResponseEntity.ok(new ApiResponse<>("Cost centers retrieved successfully", true, data));
    }



    /**
     * Get only root-level centers for tree initialization.
     */
    @GetMapping("/roots")
    @Operation(summary = "Get root-level cost centers", description = "Retrieves only top-level cost centers to initialize tree-view structures.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Root cost centers retrieved")
    public ResponseEntity<ApiResponse<List<CostCenterResponseDTO>>> getRoots() {
        List<CostCenterResponseDTO> data = costCenterService.getRoots();
        return ResponseEntity.ok(new ApiResponse<>("Root cost centers retrieved", true, data));
    }


    /**
     * Get centers enabled for accounting transactions.
     */
    @GetMapping("/movement")
    @Operation(summary = "Get movement-enabled cost centers", description = "Retrieves cost centers that allow direct accounting transactions (posting).")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Movement cost Centers retrieved")
    public ResponseEntity<ApiResponse<List<CostCenterResponseDTO>>> getMovementAccounts() {
        List<CostCenterResponseDTO> data = costCenterService.getMovementAccounts();
        return ResponseEntity.ok(new ApiResponse<>("Movement cost Centers retrieved",true,data));

    }




    /**
     * Get child centers for a specific parent.
     */
    @GetMapping("/{parentId}/children")
    @Operation(summary = "Get children of a specific cost center", description = "Retrieves all direct sub-centers for a given parent ID.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Children retrieved successfully")
    public ResponseEntity<ApiResponse<List<CostCenterResponseDTO>>> getChildren(@PathVariable Long parentId) {
        List<CostCenterResponseDTO> data = costCenterService.getChildren(parentId);
        return ResponseEntity.ok(new ApiResponse<>("Children retrieved successfully", true, data));
    }


    /**
     * Create a new cost center.
     */
    @PostMapping
    @Operation(summary = "Create a new cost center", description = "Registers a new cost center in the system hierarchy.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Cost center created successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid input data")
    public ResponseEntity<ApiResponse<CostCenterResponseDTO>> create(@Valid @RequestBody CostCenterRequest request) {
        CostCenterResponseDTO created = costCenterService.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponse<>("Cost center created successfully", true, created));
    }


    /**
     * Update an existing cost center.
     */

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing cost center", description = "Updates properties of an existing cost center.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cost center updated successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Cost center not found")
    public ResponseEntity<ApiResponse<CostCenterResponseDTO>> update(@PathVariable Long id,@Valid @RequestBody CostCenterRequest request) {
        CostCenterResponseDTO updated = costCenterService.update(id, request);
        return ResponseEntity.ok(new ApiResponse<>("Cost center updated successfully", true, updated));
    }





    /**
     * Soft delete (Deactivate) a cost center.
     * We use 204 No Content to indicate success without a body.
     */
    @DeleteMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a cost center", description = "Performs a logical deletion by setting the cost center as inactive.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cost Center deactivated successfully")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        costCenterService.deactivate(id);
        return ResponseEntity.ok(new ApiResponse<>("Cost Center deactivated successfully", true,null));
    }



/**
     * Re-activate a cost center if needed.
     */


    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate a cost center", description = "Restores a previously deactivated cost center to active status.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cost Center activated successfully")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        costCenterService.activate(id);
        return ResponseEntity.ok(new ApiResponse<>("Cost Center activated successfully", true,null));
    }

}
