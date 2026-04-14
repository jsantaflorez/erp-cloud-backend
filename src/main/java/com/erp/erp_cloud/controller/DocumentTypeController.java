package com.erp.erp_cloud.controller;

import com.erp.erp_cloud.dto.ApiResponse;
import com.erp.erp_cloud.dto.DocumentTypeRequest;
import com.erp.erp_cloud.dto.DocumentTypeResponseDTO;
import com.erp.erp_cloud.service.DocumentTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

//@CrossOrigin(
//        origins = "*",
//        allowedHeaders = "*",
//        methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS}
//)
@RestController
@RequestMapping("/api/v1/document-types")
@RequiredArgsConstructor
@Tag(name = "Document Types", description = "Management of accounting document types (e.g., Invoices, Expenses) and their consecutive numbering")
public class DocumentTypeController {

    private final DocumentTypeService documentTypeservice;


    @PostMapping
    @Operation(summary = "Create a new document type", description = "Registers a new category of accounting document with its code and initial consecutive.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Document type created successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid input or duplicate document code")
    public ResponseEntity<ApiResponse<DocumentTypeResponseDTO>> create(@Valid @RequestBody DocumentTypeRequest request) {
        DocumentTypeResponseDTO data = documentTypeservice.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponse<>("Document type created successfully", true, data));
    }

    /**
     * Updates an existing document type, including the new default balancing account.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update an existing document type", description = "Updates settings like name, default balancing account, or metadata for a document type.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Document type updated successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Document type not found")
    public ResponseEntity<ApiResponse<DocumentTypeResponseDTO>> update(
            @PathVariable Long id,
            @Valid @RequestBody DocumentTypeRequest request) {
        DocumentTypeResponseDTO data = documentTypeservice.update(id, request);
        return ResponseEntity.ok(new ApiResponse<>("Document type updated successfully", true, data));
    }



    @GetMapping
    @Operation(summary = "List all document types", description = "Retrieves the full list of available document types for the current tenant.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Document types retrieved")
    public ResponseEntity<ApiResponse<List<DocumentTypeResponseDTO>>> listAll() {
        List<DocumentTypeResponseDTO> data = documentTypeservice.listAll();
        return ResponseEntity.ok(new ApiResponse<>("Document types retrieved", true, data));
    }


    @GetMapping("/{id}")
    @Operation(summary = "Get document type by ID", description = "Retrieves specific configuration for a single document type.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Document type found")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Document type not found")
    public ResponseEntity<ApiResponse<DocumentTypeResponseDTO>> getById(@PathVariable Long id) {
        DocumentTypeResponseDTO data = documentTypeservice.findByIdDto(id);
        return ResponseEntity.ok(new ApiResponse<>("Document type found", true, data));
    }

    @GetMapping("/search")
    @Operation(summary = "Search document types by name", description = "Filters document types based on a name string match.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Search results retrieved")
    public ResponseEntity<ApiResponse<List<DocumentTypeResponseDTO>>> search(@RequestParam String name) {
        List<DocumentTypeResponseDTO> data = documentTypeservice.searchByName(name);
        return ResponseEntity.ok(new ApiResponse<>("Search results retrieved", true, data));
    }

    /**
     * Soft-deactivates a document type so it can no longer be used for new entries.
     */
    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate document type", description = "Logical deletion: prevents the document type from being used in new accounting entries.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Document type deactivated successfully")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        documentTypeservice.deactivate(id);
        return ResponseEntity.ok(new ApiResponse<>("Document type deactivated successfully", true,null));
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate document type", description = "Enables a previously deactivated document type.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Document type activated successfully")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        documentTypeservice.activate(id);
        return ResponseEntity.ok(new ApiResponse<>("Document type activated successfully", true,null));
    }

    @PatchMapping("/{id}/reset-consecutive")
    @Operation(summary = "Reset consecutive numbering", description = "Manually updates the current consecutive number for the next document to be generated.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Consecutive reset successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden: Insufficient permissions to reset sequence")
    public ResponseEntity<ApiResponse<Void>> resetConsecutive(
            @PathVariable Long id,
            @RequestParam Long newValue) {
        documentTypeservice.resetConsecutive(id, newValue);
        return ResponseEntity.ok(new ApiResponse<>("Consecutive reset successfully to " + newValue, true,null));
    }




}