package com.erp.erp_cloud.controller;

import com.erp.erp_cloud.dto.ApiResponse;
import com.erp.erp_cloud.dto.ThirdPartyRequest;
import com.erp.erp_cloud.dto.ThirdPartyResponseDTO;

import com.erp.erp_cloud.service.ThirdPartyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/third-parties")
@RequiredArgsConstructor
@Tag(name = "Third Parties", description = "Management of fiscal entities including customers, vendors, and employees")
public class ThirdPartyController {

    private final ThirdPartyService thirdPartyService;


    @PostMapping
    @Operation(summary = "Create a new third party", description = "Registers a new entity (Customer, Vendor, or Employee) in the current tenant's database.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Third Party created successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid data or duplicate identification")
    public ResponseEntity<ApiResponse<ThirdPartyResponseDTO>> create(@Valid @RequestBody ThirdPartyRequest request) {
        ThirdPartyResponseDTO created = thirdPartyService.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponse<>("Third Party created successfully", true, created));
    }



    @GetMapping
    @Operation(summary = "List/Search third parties", description = "Retrieves a paginated list of third parties.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Page retrieved successfully")
      public ResponseEntity<Page<ThirdPartyResponseDTO>> list(
            @RequestParam(required = false) String search,
            @ParameterObject @PageableDefault(size = 10, sort = "documentNumber") Pageable pageable) {
        return ResponseEntity.ok(thirdPartyService.listAll(search, pageable));
    }



    @GetMapping("/{id}")
    @Operation(summary = "Get third party by ID", description = "Retrieves the full profile of a specific third party using its internal ID.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Third party found")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Third party not found")
    public ResponseEntity<ApiResponse<ThirdPartyResponseDTO>> getById(@PathVariable Long id) {
        ThirdPartyResponseDTO data = thirdPartyService.findById(id);
        return ResponseEntity.ok(new ApiResponse<>("Third party found", true, data));
    }


    @PutMapping("/{id}")
    @Operation(summary = "Update third party info", description = "Updates contact details, fiscal information, or classification of an existing third party.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Third party updated successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Third party not found")
    public ResponseEntity<ApiResponse<ThirdPartyResponseDTO>> update(
            @PathVariable Long id,
            @Valid @RequestBody ThirdPartyRequest request) {
        ThirdPartyResponseDTO updated = thirdPartyService.update(id, request);
        return ResponseEntity.ok(new ApiResponse<>("Third party updated successfully", true, updated));
    }


    /**
     * Deactivate a Third Party (Logical delete).
     */

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate third party", description = "Logical deletion: sets the entity status to inactive to prevent use in new transactions.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Third Party deactivated successfully")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        thirdPartyService.deactivate(id);
        return ResponseEntity.ok(new ApiResponse<>("Third Party deactivated successfully", true,null));
    }



    // ACTIVATE: Uses the helper constructor (data will be null/hidden)
    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate third party", description = "Restores an inactive third party profile to active status.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Third Party activated successfully")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        thirdPartyService.activate(id);
        return ResponseEntity.ok(new ApiResponse<>("Third Party activated successfully", true,null));
    }



}