package com.erp.erp_cloud.controller;

import com.erp.erp_cloud.dto.ApiResponse;
import com.erp.erp_cloud.dto.TaxRequest;
import com.erp.erp_cloud.dto.TaxResponseDTO;
import com.erp.erp_cloud.service.TaxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/taxes")
@Tag(name = "Taxes", description = "Endpoints for managing tax configurations and tenant-specific tax rules")
@RequiredArgsConstructor
public class TaxController {

    private final TaxService taxService;

    @PostMapping
    @Operation(summary = "Create tax", description = "Creates a new tax configuration for the current tenant.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tax created successfully")
    public ResponseEntity<ApiResponse<TaxResponseDTO>> create(@Valid @RequestBody TaxRequest request) {
        TaxResponseDTO data = taxService.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponse<>("Tax created successfully", true, data));
    }

    @GetMapping
    @Operation(summary = "List all active taxes", description = "Retrieves a list of all tax configurations available for the current tenant.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List retrieved successfully")
    public ResponseEntity<ApiResponse<List<TaxResponseDTO>>> listAll() {
        List<TaxResponseDTO> data = taxService.listAll();
        return ResponseEntity.ok(new ApiResponse<>("Taxes retrieved successfully", true, data));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get tax by ID", description = "Retrieves the details of a specific tax by its unique identifier.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tax found")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Tax not found")
    public ResponseEntity<ApiResponse<TaxResponseDTO>> findById(@PathVariable Long id) {
        TaxResponseDTO data = taxService.findById(id);
        return ResponseEntity.ok(new ApiResponse<>("Tax retrieved successfully", true, data));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update tax", description = "Updates an existing tax configuration.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tax updated successfully")
    public ResponseEntity<ApiResponse<TaxResponseDTO>> update(
            @PathVariable Long id,
            @Valid @RequestBody TaxRequest request) {
        TaxResponseDTO data = taxService.update(id, request);
        return ResponseEntity.ok(new ApiResponse<>("Tax updated successfully", true, data));
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate tax", description = "Sets the tax status to inactive.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tax deactivated successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tax deactivated")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        taxService.deactivate(id);
        return ResponseEntity.ok(new ApiResponse<>("Tax deactivated successfully", true, null));
    }

    @PatchMapping("/{id}/activate")
    @Operation(
            summary = "Activate tax",
            description = "Re-enables a previously deactivated tax configuration for the current tenant."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tax activated successfully")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        taxService.activate(id);
        return ResponseEntity.ok(new ApiResponse<>("Tax activated successfully", true, null));
    }
}